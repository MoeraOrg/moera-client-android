package org.moera.android.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostingInfo {

    private String id;
    private String revisionId;
    private String receiverRevisionId;
    private Integer totalRevisions;
    private String receiverName;
    private String receiverFullName;
    private AvatarImage receiverAvatar;
    private String receiverPostingId;
    private String ownerName;
    private String ownerFullName;
    private AvatarImage ownerAvatar;
    private Body bodyPreview;
    private String saneBodyPreview;
    private Body bodySrc;
    private byte[] bodySrcHash;
    private SourceFormat bodySrcFormat;
    private Body body;
    private String saneBody;
    private String bodyFormat;
    private String heading;
    private UpdateInfo updateInfo;
    private Long createdAt;
    private Long editedAt;
    private Long deletedAt;
    private Long receiverCreatedAt;
    private Long receiverEditedAt;
    private Long receiverDeletedAt;
    private Long revisionCreatedAt;
    private Long receiverRevisionCreatedAt;
    private Long deadline;
    private Boolean draft;
    private Boolean draftPending;
    private byte[] signature;
    private Short signatureVersion;
    private List<FeedReference> feedReferences;
    private Map<String, String[]> operations;
    private AcceptedReactions acceptedReactions;
    private ClientReactionInfo clientReaction;
    private ReactionTotalsInfo reactions;
    private Boolean reactionsVisible;
    private Boolean reactionTotalsVisible;
    private List<PostingSourceInfo> sources;
    private Integer totalComments;
    private PostingSubscriptionsInfo subscriptions = new PostingSubscriptionsInfo();

    public PostingInfo() {
    }

    public PostingInfo(UUID id) {
        this.id = id.toString();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRevisionId() {
        return revisionId;
    }

    public void setRevisionId(String revisionId) {
        this.revisionId = revisionId;
    }

    public String getReceiverRevisionId() {
        return receiverRevisionId;
    }

    public void setReceiverRevisionId(String receiverRevisionId) {
        this.receiverRevisionId = receiverRevisionId;
    }

    public Integer getTotalRevisions() {
        return totalRevisions;
    }

    public void setTotalRevisions(Integer totalRevisions) {
        this.totalRevisions = totalRevisions;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getReceiverFullName() {
        return receiverFullName;
    }

    public void setReceiverFullName(String receiverFullName) {
        this.receiverFullName = receiverFullName;
    }

    public AvatarImage getReceiverAvatar() {
        return receiverAvatar;
    }

    public void setReceiverAvatar(AvatarImage receiverAvatar) {
        this.receiverAvatar = receiverAvatar;
    }

    @JsonIgnore
    public boolean isOriginal() {
        return getReceiverName() == null;
    }

    public String getReceiverPostingId() {
        return receiverPostingId;
    }

    public void setReceiverPostingId(String receiverPostingId) {
        this.receiverPostingId = receiverPostingId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getOwnerFullName() {
        return ownerFullName;
    }

    public void setOwnerFullName(String ownerFullName) {
        this.ownerFullName = ownerFullName;
    }

    public AvatarImage getOwnerAvatar() {
        return ownerAvatar;
    }

    public void setOwnerAvatar(AvatarImage ownerAvatar) {
        this.ownerAvatar = ownerAvatar;
    }

    public Body getBodyPreview() {
        return bodyPreview;
    }

    public void setBodyPreview(Body bodyPreview) {
        this.bodyPreview = bodyPreview;
    }

    public String getSaneBodyPreview() {
        return saneBodyPreview;
    }

    public void setSaneBodyPreview(String saneBodyPreview) {
        this.saneBodyPreview = saneBodyPreview;
    }

    public Body getBodySrc() {
        return bodySrc;
    }

    public void setBodySrc(Body bodySrc) {
        this.bodySrc = bodySrc;
    }

    public byte[] getBodySrcHash() {
        return bodySrcHash;
    }

    public void setBodySrcHash(byte[] bodySrcHash) {
        this.bodySrcHash = bodySrcHash;
    }

    public SourceFormat getBodySrcFormat() {
        return bodySrcFormat;
    }

    public void setBodySrcFormat(SourceFormat bodySrcFormat) {
        this.bodySrcFormat = bodySrcFormat;
    }

    public Body getBody() {
        return body;
    }

    public void setBody(Body body) {
        this.body = body;
    }

    public String getSaneBody() {
        return saneBody;
    }

    public void setSaneBody(String saneBody) {
        this.saneBody = saneBody;
    }

    public String getBodyFormat() {
        return bodyFormat;
    }

    public void setBodyFormat(String bodyFormat) {
        this.bodyFormat = bodyFormat;
    }

    public String getHeading() {
        return heading;
    }

    public void setHeading(String heading) {
        this.heading = heading;
    }

    public UpdateInfo getUpdateInfo() {
        return updateInfo;
    }

    public void setUpdateInfo(UpdateInfo updateInfo) {
        this.updateInfo = updateInfo;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(Long editedAt) {
        this.editedAt = editedAt;
    }

    public Long getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Long deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Long getReceiverCreatedAt() {
        return receiverCreatedAt;
    }

    public void setReceiverCreatedAt(Long receiverCreatedAt) {
        this.receiverCreatedAt = receiverCreatedAt;
    }

    public Long getReceiverEditedAt() {
        return receiverEditedAt;
    }

    public void setReceiverEditedAt(Long receiverEditedAt) {
        this.receiverEditedAt = receiverEditedAt;
    }

    public Long getReceiverDeletedAt() {
        return receiverDeletedAt;
    }

    public void setReceiverDeletedAt(Long receiverDeletedAt) {
        this.receiverDeletedAt = receiverDeletedAt;
    }

    public Long getRevisionCreatedAt() {
        return revisionCreatedAt;
    }

    public void setRevisionCreatedAt(Long revisionCreatedAt) {
        this.revisionCreatedAt = revisionCreatedAt;
    }

    public Long getReceiverRevisionCreatedAt() {
        return receiverRevisionCreatedAt;
    }

    public void setReceiverRevisionCreatedAt(Long receiverRevisionCreatedAt) {
        this.receiverRevisionCreatedAt = receiverRevisionCreatedAt;
    }

    public Long getDeadline() {
        return deadline;
    }

    public void setDeadline(Long deadline) {
        this.deadline = deadline;
    }

    public Boolean getDraft() {
        return draft;
    }

    public void setDraft(Boolean draft) {
        this.draft = draft;
    }

    public Boolean getDraftPending() {
        return draftPending;
    }

    public void setDraftPending(Boolean draftPending) {
        this.draftPending = draftPending;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public Short getSignatureVersion() {
        return signatureVersion;
    }

    public void setSignatureVersion(Short signatureVersion) {
        this.signatureVersion = signatureVersion;
    }

    public List<FeedReference> getFeedReferences() {
        return feedReferences;
    }

    public void setFeedReferences(List<FeedReference> feedReferences) {
        this.feedReferences = feedReferences;
    }

    public FeedReference getFeedReference(String feedName) {
        if (getFeedReferences() == null) {
            return null;
        }
        return getFeedReferences().stream().filter(fr -> fr.getFeedName().equals(feedName)).findFirst().orElse(null);
    }

    public Map<String, String[]> getOperations() {
        return operations;
    }

    public void setOperations(Map<String, String[]> operations) {
        this.operations = operations;
    }

    public AcceptedReactions getAcceptedReactions() {
        return acceptedReactions;
    }

    public void setAcceptedReactions(AcceptedReactions acceptedReactions) {
        this.acceptedReactions = acceptedReactions;
    }

    public ClientReactionInfo getClientReaction() {
        return clientReaction;
    }

    public void setClientReaction(ClientReactionInfo clientReaction) {
        this.clientReaction = clientReaction;
    }

    public ReactionTotalsInfo getReactions() {
        return reactions;
    }

    public void setReactions(ReactionTotalsInfo reactions) {
        this.reactions = reactions;
    }

    public Boolean getReactionsVisible() {
        return reactionsVisible;
    }

    public void setReactionsVisible(Boolean reactionsVisible) {
        this.reactionsVisible = reactionsVisible;
    }

    public Boolean getReactionTotalsVisible() {
        return reactionTotalsVisible;
    }

    public void setReactionTotalsVisible(Boolean reactionTotalsVisible) {
        this.reactionTotalsVisible = reactionTotalsVisible;
    }

    public List<PostingSourceInfo> getSources() {
        return sources;
    }

    public void setSources(List<PostingSourceInfo> sources) {
        this.sources = sources;
    }

    public Integer getTotalComments() {
        return totalComments;
    }

    public void setTotalComments(Integer totalComments) {
        this.totalComments = totalComments;
    }

    public PostingSubscriptionsInfo getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(PostingSubscriptionsInfo subscriptions) {
        this.subscriptions = subscriptions;
    }

}
