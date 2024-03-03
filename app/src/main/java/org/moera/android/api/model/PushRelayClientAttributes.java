package org.moera.android.api.model;

public class PushRelayClientAttributes {

    private PushRelayType type;

    private String clientId;

    private String lang;

    public PushRelayType getType() {
        return type;
    }

    public void setType(PushRelayType type) {
        this.type = type;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

}
