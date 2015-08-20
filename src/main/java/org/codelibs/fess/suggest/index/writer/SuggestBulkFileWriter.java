package org.codelibs.fess.suggest.index.writer;

import org.codelibs.fess.suggest.entity.SuggestItem;
import org.codelibs.fess.suggest.settings.SuggestSettings;
import org.elasticsearch.client.Client;

public class SuggestBulkFileWriter implements SuggestWriter {
    @Override
    public SuggestWriterResult write(final Client client, final SuggestSettings settings, final String index, final String type,
            final SuggestItem[] items) {
        throw new UnsupportedOperationException("not yet.");
    }

    @Override
    public SuggestWriterResult delete(final Client client, final SuggestSettings settings, final String index, final String type,
            final String id) {
        throw new UnsupportedOperationException("not yet.");
    }

    @Override
    public SuggestWriterResult deleteByQuery(final Client client, final SuggestSettings settings, final String index, final String type,
            final String queryString) {
        throw new UnsupportedOperationException("deleteByQuery is unsupported.");
    }
}
