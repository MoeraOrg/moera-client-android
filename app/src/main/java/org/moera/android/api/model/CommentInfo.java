package org.moera.android.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommentInfo {

    private String id;
    private String ownerName;
    private String ownerFullName;
    private AvatarImage ownerAvatar;
    private String postingId;
    private String postingRevisionId;
    private String revisionId;
    private Integer totalRevisions;
    private Body bodyPreview;
    private String saneBodyPreview;
    private Body bodySrc;
    private byte[] bodySrcHash;
    private SourceFormat bodySrcFormat;
    private Body body;
    private String saneBody;
    private String bodyFormat;
    private String heading;
    private RepliedTo repliedTo;
    private long moment;
    private Long createdAt;
    private Long editedAt;
    private Long deletedAt;
    private Long revisionCreatedAt;
    private Long deadline;
    private byte[] digest;
    private byte[] signature;
    private Short signatureVersion;
    private Map<String, String> operations;
    private AcceptedReactions acceptedReactions;
    private ClientReactionInfo clientReaction;
    private ReactionTotalsInfo reactions;

    public CommentInfo() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getPostingId() {
        return postingId;
    }

    public void setPostingId(String postingId) {
        this.postingId = postingId;
    }

    public String getPostingRevisionId() {
        return postingRevisionId;
    }

    public void setPostingRevisionId(String postingRevisionId) {
        this.postingRevisionId = postingRevisionId;
    }

    public String getRevisionId() {
        return revisionId;
    }

    public void setRevisionId(String revisionId) {
        this.revisionId = revisionId;
    }

    public Integer getTotalRevisions() {
        return totalRevisions;
    }

    public void setTotalRevisions(Integer totalRevisions) {
        this.totalRevisions = totalRevisions;
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

    public RepliedTo getRepliedTo() {
        return repliedTo;
    }

    public void setRepliedTo(RepliedTo repliedTo) {
        this.repliedTo = repliedTo;
    }

    @JsonIgnore
    public String getRepliedToId() {
        return getRepliedTo() != null ? getRepliedTo().getId() : null;
    }

    @JsonIgnore
    public String getRepliedToRevisionId() {
        return getRepliedTo() != null ? getRepliedTo().getRevisionId() : null;
    }

    @JsonIgnore
    public String getRepliedToName() {
        return getRepliedTo() != null ? getRepliedTo().getName() : null;
    }

    @JsonIgnore
    public String getRepliedToFullName() {
        return getRepliedTo() != null ? getRepliedTo().getFullName() : null;
    }

    @JsonIgnore
    public AvatarImage getRepliedToAvatar() {
        return getRepliedTo() != null ? getRepliedTo().getAvatar() : null;
    }

    public long getMoment() {
        return moment;
    }

    public void setMoment(long moment) {
        this.moment = moment;
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

    public Long getRevisionCreatedAt() {
        return revisionCreatedAt;
    }

    public void setRevisionCreatedAt(Long revisionCreatedAt) {
        this.revisionCreatedAt = revisionCreatedAt;
    }

    public Long getDeadline() {
        return deadline;
    }

    public void setDeadline(Long deadline) {
        this.deadline = deadline;
    }

    public byte[] getDigest() {
        return digest;
    }

    public void setDigest(byte[] digest) {
        this.digest = digest;
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

    public Map<String, String> getOperations() {
        return operations;
    }

    public void setOperations(Map<String, String> operations) {
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

}
