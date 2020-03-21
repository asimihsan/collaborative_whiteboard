package lambda;

import com.google.common.base.MoreObjects;

public class GetWhiteboardRequest {
    private String identifier;

    public GetWhiteboardRequest() {

    }

    public GetWhiteboardRequest(final String identifier) {
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("identifier", identifier)
                .toString();
    }
}
