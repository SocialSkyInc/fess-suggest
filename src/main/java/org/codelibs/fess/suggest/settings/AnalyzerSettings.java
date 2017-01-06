package org.codelibs.fess.suggest.settings;

import org.codelibs.core.lang.StringUtil;
import org.codelibs.fess.suggest.analysis.SuggestAnalyzer;
import org.codelibs.fess.suggest.exception.SuggestSettingsException;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.client.Client;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class AnalyzerSettings {
    public static final String readingAnalyzerName = "reading_analyzer";
    public static final String readingTermAnalyzerName = "reading_term_analyzer";
    public static final String normalizeAnalyzerName = "normalize_analyzer";
    public static final String contentsAnalyzerName = "contents_analyzer";

    protected final Client client;
    protected final String analyzerSettingsIndexName;

    public static final String[] SUPPORTED_LANGUAGES = new String[] { "ar", "bg", "bn", "ca", "cs", "da", "de", "el", "en", "es", "et",
            "fa", "fi", "fr", "gu", "he", "hi", "hr", "hu", "id", "it", "ja", "ko", "lt", "lv", "mk", "ml", "nl", "no", "pa", "pl", "pt",
            "ro", "ru", "si", "sq", "sv", "ta", "te", "th", "tl", "tr", "uk", "ur", "vi", "zh-cn", "zh-tw" };

    public AnalyzerSettings(final Client client, final String settingsIndexName) {
        this.client = client;
        analyzerSettingsIndexName = createAnalyzerSettingsIndexName(settingsIndexName);
    }

    public void init() {
        try {
            IndicesExistsResponse response = client.admin().indices().prepareExists(analyzerSettingsIndexName).execute().actionGet();
            if (!response.isExists()) {
                createAnalyzerSettings(loadIndexSettings());
            }
        } catch (IOException e) {
            throw new SuggestSettingsException("Failed to create mappings.");
        }
    }

    public String getAnalyzerSettingsIndexName() {
        return analyzerSettingsIndexName;
    }

    public String getReadingAnalyzerName(final String lang) {
        return isSupportedLanguage(lang) ? readingAnalyzerName + '_' + lang : readingAnalyzerName;
    }

    public String getReadingTermAnalyzerName(final String lang) {
        return isSupportedLanguage(lang) ? readingTermAnalyzerName + '_' + lang : readingTermAnalyzerName;
    }

    public String getNormalizeAnalyzerName(final String lang) {
        return isSupportedLanguage(lang) ? normalizeAnalyzerName + '_' + lang : normalizeAnalyzerName;
    }

    public String getContentsAnalyzerName(final String lang) {
        return isSupportedLanguage(lang) ? contentsAnalyzerName + '_' + lang : contentsAnalyzerName;
    }

    public void updateAnalyzer(final Map<String, Object> settings) {
        client.admin().indices().prepareCreate(analyzerSettingsIndexName).setSettings(settings).execute().actionGet();
    }

    protected void deleteAnalyzerSettings() {
        client.admin().indices().prepareDelete(analyzerSettingsIndexName).execute().actionGet();
    }

    protected void createAnalyzerSettings(final String settings) {
        client.admin().indices().prepareCreate(analyzerSettingsIndexName).setSettings(settings).execute().actionGet();
    }

    protected void createAnalyzerSettings(final Map<String, Object> settings) {
        client.admin().indices().prepareCreate(analyzerSettingsIndexName).setSettings(settings).execute().actionGet();
    }

    protected String createAnalyzerSettingsIndexName(final String settingsIndexName) {
        return settingsIndexName + "_analyzer";
    }

    protected String loadIndexSettings() throws IOException {
        final String dictionaryPath = System.getProperty("fess.dictionary.path", StringUtil.EMPTY);
        final StringBuilder sb = new StringBuilder();
        try (BufferedReader br =
                new BufferedReader(new InputStreamReader(this.getClass().getClassLoader()
                        .getResourceAsStream("suggest_indices/suggest_analyzer.json")));) {

            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString().replaceAll(Pattern.quote("${fess.dictionary.path}"), dictionaryPath);
    }

    public class DefaultContentsAnalyzer implements SuggestAnalyzer {
        public List<AnalyzeResponse.AnalyzeToken> analyze(final String text, final String lang) {
            final AnalyzeResponse analyzeResponse =
                    client.admin().indices().prepareAnalyze(analyzerSettingsIndexName, text).setAnalyzer(getContentsAnalyzerName(lang))
                            .execute().actionGet();
            return analyzeResponse.getTokens();
        }
    }

    public static boolean isSupportedLanguage(final String lang) {
        return (StringUtil.isNotBlank(lang) && Stream.of(SUPPORTED_LANGUAGES).anyMatch(lang::equals));
    }
}
