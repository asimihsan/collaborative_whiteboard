package lambda;

import com.google.common.base.MoreObjects;

public class SetWhiteboardResponse {
    private String identifier;
    private boolean successful;
    private String content;

    public SetWhiteboardResponse() {
    }

    public SetWhiteboardResponse(String identifier, boolean successful, String content) {
        this.identifier = identifier;
        this.successful = successful;
        this.content = content;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
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
                .add("successful", successful)
                .add("content", content)
                .toString();
    }
}
