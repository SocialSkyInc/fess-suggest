package org.codelibs.fess.suggest.request.suggest;

import java.io.IOException;
import java.lang.Character.UnicodeBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.base.Strings;
import org.codelibs.fess.suggest.concurrent.Deferred;
import org.codelibs.fess.suggest.constants.FieldNames;
import org.codelibs.fess.suggest.constants.SuggestConstants;
import org.codelibs.fess.suggest.converter.ReadingConverter;
import org.codelibs.fess.suggest.entity.SuggestItem;
import org.codelibs.fess.suggest.exception.SuggesterException;
import org.codelibs.fess.suggest.normalizer.Normalizer;
import org.codelibs.fess.suggest.request.Request;
import org.codelibs.fess.suggest.util.SuggestUtil;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;

public class SuggestRequest extends Request<SuggestResponse> {
    private String index = null;

    private String type = null;

    private String query = "";

    private int size = 10;

    private final List<String> tags = new ArrayList<>();

    private final List<String> roles = new ArrayList<>();

    private final List<String> fields = new ArrayList<>();

    private final List<String> kinds = new ArrayList<>();

    private final List<String> languages = new ArrayList<>();

    private boolean suggestDetail = true;

    private ReadingConverter readingConverter;

    private Normalizer normalizer;

    private float prefixMatchWeight = 2.0f;

    private boolean matchWordFirst = true;

    private boolean skipDuplicateWords = true;

    public void setIndex(final String index) {
        this.index = index;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public void setSize(final int size) {
        this.size = size;
    }

    public void setQuery(final String query) {
        this.query = query;
    }

    public void addTag(final String tag) {
        this.tags.add(tag);
    }

    public void addRole(final String role) {
        this.roles.add(role);
    }

    public void addField(final String field) {
        this.fields.add(field);
    }

    public void addKind(final String kind) {
        this.kinds.add(kind);
    }

    public void setSuggestDetail(final boolean suggestDetail) {
        this.suggestDetail = suggestDetail;
    }

    public void setReadingConverter(final ReadingConverter readingConverter) {
        this.readingConverter = readingConverter;
    }

    public void setNormalizer(final Normalizer normalizer) {
        this.normalizer = normalizer;
    }

    public void setPrefixMatchWeight(final float prefixMatchWeight) {
        this.prefixMatchWeight = prefixMatchWeight;
    }

    public void setMatchWordFirst(final boolean matchWordFirst) {
        this.matchWordFirst = matchWordFirst;
    }

    public void setSkipDuplicateWords(final boolean skipDuplicateWords) {
        this.skipDuplicateWords = skipDuplicateWords;
    }

    public void addLang(final String lang) {
        this.languages.add(lang);
    }

    @Override
    protected String getValidationError() {
        return null;
    }

    @Override
    protected void processRequest(final Client client, final Deferred<SuggestResponse> deferred) {
        final SearchRequestBuilder builder = client.prepareSearch(index);
        if (Strings.isNullOrEmpty(type)) {
            builder.setTypes(type);
        }

        if (skipDuplicateWords) {
            builder.setSize(size * 2);
        } else {
            builder.setSize(size);
        }

        // set query.
        final QueryBuilder q = buildQuery(query);

        // set function score
        final QueryBuilder queryBuilder = buildFunctionScoreQuery(query, q);
        builder.addSort("_score", SortOrder.DESC);

        //set filter query.
        final List<QueryBuilder> filterList = new ArrayList<>(10);
        if (!tags.isEmpty()) {
            filterList.add(buildFilterQuery(FieldNames.TAGS, tags));
        }

        roles.add(SuggestConstants.DEFAULT_ROLE);
        if (!roles.isEmpty()) {
            filterList.add(buildFilterQuery(FieldNames.ROLES, roles));
        }

        if (!fields.isEmpty()) {
            filterList.add(buildFilterQuery(FieldNames.FIELDS, fields));
        }

        if (!kinds.isEmpty()) {
            filterList.add(buildFilterQuery(FieldNames.KINDS, kinds));
        }

        if (filterList.size() > 0) {
            final BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            boolQueryBuilder.must(queryBuilder);
            filterList.forEach(boolQueryBuilder::filter);
            builder.setQuery(boolQueryBuilder);
        } else {
            builder.setQuery(queryBuilder);
        }

        builder.execute(new ActionListener<SearchResponse>() {
            @Override
            public void onResponse(final SearchResponse searchResponse) {
                if (searchResponse.getFailedShards() > 0) {
                    deferred.reject(new SuggesterException("Search failure. Failed shards num:" + searchResponse.getFailedShards()));
                } else {
                    deferred.resolve(createResponse(searchResponse));
                }
            }

            @Override
            public void onFailure(final Throwable e) {
                deferred.reject(new SuggesterException(e.getMessage(), e));
            }
        });
    }

    private boolean isSingleWordQuery(final String query) {
        return !Strings.isNullOrEmpty(query) && !query.contains(" ") && !query.contains("　");
    }

    protected QueryBuilder buildQuery(final String q) {
        try {
            final QueryBuilder queryBuilder;
            if (Strings.isNullOrEmpty(q)) {
                queryBuilder = QueryBuilders.matchAllQuery();
            } else {
                final boolean prefixQuery = !q.endsWith(" ") && !q.endsWith("　");
                List<String> readingList = new ArrayList<>();

                final String[] langsArray = languages.toArray(new String[languages.size()]);

                final BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
                final String[] queries = q.replaceAll("　", " ").replaceAll(" +", " ").trim().split(" ");
                for (int i = 0; i < queries.length; i++) {
                    final String fieldName = FieldNames.READING_PREFIX + i;

                    final String query;
                    if (normalizer == null) {
                        query = queries[i];
                    } else {
                        query = normalizer.normalize(queries[i], langsArray);
                    }

                    if (readingConverter == null) {
                        readingList.add(query);
                    } else {
                        //TODO locale
                        readingList = readingConverter.convert(query, langsArray);
                    }

                    final BoolQueryBuilder readingQueryBuilder = QueryBuilders.boolQuery().minimumNumberShouldMatch(1);
                    final int readingNum = readingList.size();
                    for (int readingCount = 0; readingCount < readingNum; readingCount++) {
                        final String reading = readingList.get(readingCount);
                        if (i + 1 == queries.length && prefixQuery) {
                            readingQueryBuilder.should(QueryBuilders.prefixQuery(fieldName, reading));
                        } else {
                            readingQueryBuilder.should(QueryBuilders.termQuery(fieldName, reading));
                        }
                    }
                    readingList.clear();
                    boolQueryBuilder.must(readingQueryBuilder);
                }
                queryBuilder = boolQueryBuilder;
            }

            return queryBuilder;
        } catch (final IOException e) {
            throw new SuggesterException("Failed to create queryString.", e);
        }
    }

    protected QueryBuilder buildFilterQuery(final String fieldName, final List<String> words) {
        final BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery().minimumNumberShouldMatch(1);
        words.stream().forEach(word -> boolQueryBuilder.should(QueryBuilders.termQuery(fieldName, word)));
        return boolQueryBuilder;
    }

    protected QueryBuilder buildFunctionScoreQuery(final String query, final QueryBuilder queryBuilder) {
        final FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(queryBuilder);
        if (isSingleWordQuery(query) && !isHiraganaQuery(query)) {
            functionScoreQueryBuilder.add(QueryBuilders.prefixQuery(FieldNames.TEXT, query),
                    ScoreFunctionBuilders.weightFactorFunction(prefixMatchWeight));
        }

        functionScoreQueryBuilder.add(ScoreFunctionBuilders.fieldValueFactorFunction(FieldNames.DOC_FREQ).missing(0.1f)
                .modifier(FieldValueFactorFunction.Modifier.LOG2P).setWeight(1.0F));
        functionScoreQueryBuilder.add(ScoreFunctionBuilders.fieldValueFactorFunction(FieldNames.QUERY_FREQ).missing(0.1f)
                .modifier(FieldValueFactorFunction.Modifier.LOG2P).setWeight(1.0F));
        functionScoreQueryBuilder.add(ScoreFunctionBuilders.fieldValueFactorFunction(FieldNames.USER_BOOST).missing(1f).setWeight(1.0F));

        functionScoreQueryBuilder.boostMode(CombineFunction.REPLACE);
        functionScoreQueryBuilder.scoreMode("multiply");

        return functionScoreQueryBuilder;
    }

    protected SuggestResponse createResponse(final SearchResponse searchResponse) {
        final SearchHit[] hits = searchResponse.getHits().getHits();
        final List<String> words = new ArrayList<>();
        final List<String> firstWords = new ArrayList<>();
        final List<String> secondWords = new ArrayList<>();
        final List<SuggestItem> firstItems = new ArrayList<>();
        final List<SuggestItem> secondItems = new ArrayList<>();

        final String index;
        if (hits.length > 0) {
            index = hits[0].index();
        } else {
            index = SuggestConstants.EMPTY_STRING;
        }

        boolean singleWordQuery = isSingleWordQuery(query);
        boolean hiraganaQuery = isHiraganaQuery(query);
        for (int i = 0; i < hits.length && words.size() < size; i++) {
            final SearchHit hit = hits[i];

            final Map<String, Object> source = hit.sourceAsMap();
            final String text = source.get(FieldNames.TEXT).toString();
            if (skipDuplicateWords) {
                final String duplicateCheckStr = text.replace(" ", "");
                if (words.stream().map(word -> word.replace(" ", "")).anyMatch(word -> word.equals(duplicateCheckStr))) {
                    // skip duplicate word.
                    continue;
                }
            }

            words.add(text);
            final boolean isFirstWords = isFirstWordMatching(singleWordQuery, hiraganaQuery, text);
            if (isFirstWords) {
                firstWords.add(text);
            } else {
                secondWords.add(text);
            }

            if (suggestDetail) {
                int readingCount = 0;
                Object readingObj;
                final List<String[]> readings = new ArrayList<>();
                while ((readingObj = source.get(FieldNames.READING_PREFIX + readingCount++)) != null) {
                    final List<String> reading = SuggestUtil.getAsList(readingObj);
                    readings.add(reading.toArray(new String[reading.size()]));
                }

                final List<String> fields = SuggestUtil.getAsList(source.get(FieldNames.FIELDS));
                final List<String> tags = SuggestUtil.getAsList(source.get(FieldNames.TAGS));
                final List<String> roles = SuggestUtil.getAsList(source.get(FieldNames.ROLES));
                final List<String> kinds = SuggestUtil.getAsList(source.get(FieldNames.KINDS));
                final List<String> language = SuggestUtil.getAsList(source.get(FieldNames.LANGUAGES));
                SuggestItem.Kind kind;
                long freq;
                if (SuggestItem.Kind.USER.toString().equals(kinds.get(0))) {
                    kind = SuggestItem.Kind.USER;
                    freq = 0;
                } else if (SuggestItem.Kind.QUERY.toString().equals(kinds.get(0))) {
                    kind = SuggestItem.Kind.QUERY;
                    freq = Long.parseLong(source.get(FieldNames.QUERY_FREQ).toString());
                } else {
                    kind = SuggestItem.Kind.DOCUMENT;
                    freq = Long.parseLong(source.get(FieldNames.DOC_FREQ).toString());
                }

                SuggestItem item =
                        new SuggestItem(text.split(" "), readings.toArray(new String[readings.size()][]), fields.toArray(new String[fields
                                .size()]), freq, Float.valueOf(source.get(FieldNames.USER_BOOST).toString()), tags.toArray(new String[tags
                                .size()]), roles.toArray(new String[tags.size()]), language.toArray(new String[language.size()]), kind);
                if (isFirstWords) {
                    firstItems.add(item);
                } else {
                    secondItems.add(item);
                }
            }
        }
        firstWords.addAll(secondWords);
        firstItems.addAll(secondItems);
        return new SuggestResponse(index, searchResponse.getTookInMillis(), firstWords, searchResponse.getHits().totalHits(), firstItems);
    }

    protected boolean isFirstWordMatching(final boolean singleWordQuery, final boolean hiraganaQuery, final String text) {
        if (matchWordFirst && !hiraganaQuery && singleWordQuery && text.contains(query)) {
            if (query.length() == 1) {
                return UnicodeBlock.of(query.charAt(0)) != UnicodeBlock.HIRAGANA;
            }
            return true;
        }
        return false;
    }

    protected boolean isHiraganaQuery(final String query) {
        return query.matches("^[\\u3040-\\u309F]+$");
    }
}
