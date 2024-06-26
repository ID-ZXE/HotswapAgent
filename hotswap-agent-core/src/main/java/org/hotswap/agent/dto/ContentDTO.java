package org.hotswap.agent.dto;

import java.util.List;
import java.util.Map;

public class ContentDTO {

    private Map<String, String> content;

    private List<byte[]> classes;

    private String app;

    private String profile;

    private String lane;

    private Boolean toClasspath;

    private Map<String, String> extraData;

    public Map<String, String> getContent() {
        return content;
    }

    public void setContent(Map<String, String> content) {
        this.content = content;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getLane() {
        return lane;
    }

    public void setLane(String lane) {
        this.lane = lane;
    }

    public Boolean getToClasspath() {
        return toClasspath;
    }

    public void setToClasspath(Boolean toClasspath) {
        this.toClasspath = toClasspath;
    }

    public List<byte[]> getClasses() {
        return classes;
    }

    public void setClasses(List<byte[]> classes) {
        this.classes = classes;
    }

    public Map<String, String> getExtraData() {
        return extraData;
    }

    public void setExtraData(Map<String, String> extraData) {
        this.extraData = extraData;
    }
}
