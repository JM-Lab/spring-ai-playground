package jm.kr.spring.ai.playground.service.vectorstore;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VectorStoreDocumentService {

    public record TokenTextSplitInfo(int chunkSize, int minChunkSizeChars, int minChunkLengthToEmbed,
                                     int maxNumChunks, boolean keepSeparator) {}

    private final Map<String, TokenTextSplitter> splitters;

    public final static TokenTextSplitInfo DEFAULT_TOKEN_TEXT_SPLIT_INFO = new TokenTextSplitInfo(800, 350, 5, 10000
            , true);
    private final TokenTextSplitter defaultTokenTextSplitter;

    private final Map<String, VectorStoreDocumentInfo> documentInfos = new ConcurrentHashMap<>();

    public VectorStoreDocumentService() {
        this.splitters = new WeakHashMap<>();
        this.defaultTokenTextSplitter = newTokenTextSplitter(DEFAULT_TOKEN_TEXT_SPLIT_INFO);
    }

    public List<Document> split(String resourceUrl) {
        return split(this.defaultTokenTextSplitter, new TikaDocumentReader(resourceUrl));
    }

    public List<Document> split(String resourceUrl, TokenTextSplitInfo tokenTextSplitInfo) {
        return split(this.splitters.computeIfAbsent(tokenTextSplitInfo.toString(),
                key -> newTokenTextSplitter(tokenTextSplitInfo)), new TikaDocumentReader(resourceUrl));
    }

    private TokenTextSplitter newTokenTextSplitter(TokenTextSplitInfo tokenTextSplitInfo) {
        return new TokenTextSplitter(tokenTextSplitInfo.chunkSize(), tokenTextSplitInfo.minChunkSizeChars(),
                tokenTextSplitInfo.minChunkLengthToEmbed(), tokenTextSplitInfo.maxNumChunks(),
                tokenTextSplitInfo.keepSeparator());
    }

    public List<Document> split(String resourceUrl, TokenTextSplitter tokenTextSplitter) {
        return split(tokenTextSplitter, new TikaDocumentReader(resourceUrl));
    }

    private List<Document> split(TextSplitter textSplitter, DocumentReader documentReader) {
        return textSplitter.split(documentReader.read());
    }

    public VectorStoreDocumentInfo updateDocument(VectorStoreDocumentInfo vectorStoreDocumentInfo, String title) {
        VectorStoreDocumentInfo updateVectorStoreDocumentInfo = vectorStoreDocumentInfo.newTitle(title);
        this.documentInfos.put(vectorStoreDocumentInfo.docId(), updateVectorStoreDocumentInfo);
        return updateVectorStoreDocumentInfo;
    }

    public void deleteDocumentInfo(String docId) {
        this.documentInfos.remove(docId);
    }

    public List<VectorStoreDocumentInfo> getDocumentList() {
        return this.documentInfos.values().stream()
                .sorted(Comparator.comparingLong(VectorStoreDocumentInfo::updateTimestamp).reversed()).toList();
    }

    public int getDocumentTotal() {
        return this.documentInfos.size();
    }

}
