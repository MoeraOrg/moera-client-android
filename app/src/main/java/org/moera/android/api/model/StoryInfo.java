package org.moera.android.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StoryInfo {

    private String id;
    private String feedName;
    private StoryType storyType;
    private long createdAt;
    private long publishedAt;
    private boolean pinned;
    private Long moment;
    private Boolean viewed;
    private Boolean read;
    private Boolean satisfied;
    private String summaryNodeName;
    private String summaryFullName;
    private AvatarImage summaryAvatar;
    private String summary;
    private StorySummaryData summaryData;
    private PostingInfo posting;
    private String postingId;
    private CommentInfo comment;
    private String commentId;
    private String remoteNodeName;
    private String remoteFullName;
    private String remotePostingId;
    private String remoteCommentId;
    private String remoteMediaId;
    private Map<String, String> operations;

    public StoryInfo() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFeedName() {
        return feedName;
    }

    public void setFeedName(String feedName) {
        this.feedName = feedName;
    }

    public StoryType getStoryType() {
        return storyType;
    }

    public void setStoryType(StoryType storyType) {
        this.storyType = storyType;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(long publishedAt) {
        this.publishedAt = publishedAt;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public Long getMoment() {
        return moment;
    }

    public void setMoment(Long moment) {
        this.moment = moment;
    }

    public Boolean getViewed() {
        return viewed;
    }

    public void setViewed(Boolean viewed) {
        this.viewed = viewed;
    }

    public Boolean getRead() {
        return read;
    }

    public void setRead(Boolean read) {
        this.read = read;
    }

    public Boolean getSatisfied() {
        return satisfied;
    }

    public void setSatisfied(Boolean satisfied) {
        this.satisfied = satisfied;
    }

    public String getSummaryNodeName() {
        return summaryNodeName;
    }

    public void setSummaryNodeName(String summaryNodeName) {
        this.summaryNodeName = summaryNodeName;
    }

    public String getSummaryFullName() {
        return summaryFullName;
    }

    public void setSummaryFullName(String summaryFullName) {
        this.summaryFullName = summaryFullName;
    }

    public AvatarImage getSummaryAvatar() {
        return summaryAvatar;
    }

    public void setSummaryAvatar(AvatarImage summaryAvatar) {
        this.summaryAvatar = summaryAvatar;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public StorySummaryData getSummaryData() {
        return summaryData;
    }

    public void setSummaryData(StorySummaryData summaryData) {
        this.summaryData = summaryData;
    }

    public PostingInfo getPosting() {
        return posting;
    }

    public void setPosting(PostingInfo posting) {
        this.posting = posting;
    }

    public String getPostingId() {
        return postingId;
    }

    public void setPostingId(String postingId) {
        this.postingId = postingId;
    }

    public CommentInfo getComment() {
        return comment;
    }

    public void setComment(CommentInfo comment) {
        this.comment = comment;
    }

    public String getCommentId() {
        return commentId;
    }

    public void setCommentId(String commentId) {
        this.commentId = commentId;
    }

    public String getRemoteNodeName() {
        return remoteNodeName;
    }

    public void setRemoteNodeName(String remoteNodeName) {
        this.remoteNodeName = remoteNodeName;
    }

    public String getRemoteFullName() {
        return remoteFullName;
    }

    public void setRemoteFullName(String remoteFullName) {
        this.remoteFullName = remoteFullName;
    }

    public String getRemotePostingId() {
        return remotePostingId;
    }

    public void setRemotePostingId(String remotePostingId) {
        this.remotePostingId = remotePostingId;
    }

    public String getRemoteCommentId() {
        return remoteCommentId;
    }

    public void setRemoteCommentId(String remoteCommentId) {
        this.remoteCommentId = remoteCommentId;
    }

    public String getRemoteMediaId() {
        return remoteMediaId;
    }

    public void setRemoteMediaId(String remoteMediaId) {
        this.remoteMediaId = remoteMediaId;
    }

    public Map<String, String> getOperations() {
        return operations;
    }

    public void setOperations(Map<String, String> operations) {
        this.operations = operations;
    }

}
