package lambda;

import com.google.common.base.MoreObjects;

public class GetWhiteboardResponse {
    private String identifier;
    private String content;

    public GetWhiteboardResponse(final String identifier, final String content) {
        this.identifier = identifier;
        this.content = content;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("identifier", identifier)
                .add("content", content)
                .toString();
    }
}
