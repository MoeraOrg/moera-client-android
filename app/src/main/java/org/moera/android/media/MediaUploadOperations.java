package org.moera.android.media;

import static android.content.Context.MODE_PRIVATE;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import androidx.annotation.Nullable;
import okhttp3.OkHttpClient;
import org.moera.android.BuildConfig;
import org.moera.android.Preferences;
import org.moera.android.media.api.MediaUploadApi;
import org.moera.android.media.database.MediaDatabase;
import org.moera.android.media.database.MediaUploadDao;
import org.moera.android.media.database.MediaUploadEntity;
import org.moera.android.settings.Settings;
import org.moera.android.util.MimeUtil;
import org.moera.lib.node.types.MediaUploadInfo;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Ordered bridge commands and durable native upload ownership. */
public class MediaUploadOperations {

    private static final long MAX_THUMBNAIL_BYTES = 1024 * 1024;
    private static volatile MediaUploadOperations mediaUploadOperations;

    private final Context context;
    private final SelectedMediaStore selectedMediaStore;
    private final MediaUploadDao dao;
    private final MediaUploadApi api;
    private final MediaUploadScheduler scheduler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
    private final Set<String> activeRuns = ConcurrentHashMap.newKeySet();
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();
    private final Set<String> stoppedRuns = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Thread> activeThreads = new ConcurrentHashMap<>();
    private final Set<MediaUploadListener> listeners = new CopyOnWriteArraySet<>();
    private final ConcurrentHashMap<String, Long> lastProgressAt = new ConcurrentHashMap<>();
    private final Object databaseLock = new Object();

    private MediaUploadOperations(Context context, boolean allowInsecureHttp) {
        this.context = context.getApplicationContext();
        selectedMediaStore = new SelectedMediaStore(this.context);
        dao = MediaDatabase.getInstance(this.context).mediaUploadDao();
        scheduler = new MediaUploadScheduler(this.context);
        api = new MediaUploadApi(
            new OkHttpClient(),
            "Moera client for Android/" + BuildConfig.VERSION_NAME,
            allowInsecureHttp
        );
    }

    public static MediaUploadOperations getInstance(Context context, boolean allowInsecureHttp) {
        if (mediaUploadOperations == null) {
            synchronized (MediaUploadOperations.class) {
                if (mediaUploadOperations == null) {
                    mediaUploadOperations = new MediaUploadOperations(context, allowInsecureHttp);
                }
            }
        }
        return mediaUploadOperations;
    }

    public static MediaUploadOperations getInstance(Context context) {
        boolean allowInsecureHttp = false;
        try {
            allowInsecureHttp = Boolean.TRUE.equals(new Settings(context).getBool("mobile.developer"));
        } catch (IOException ignored) {
            // Production homes require HTTPS; failure to read settings keeps that safe default.
        }
        return getInstance(context, allowInsecureHttp);
    }

    public void addListener(MediaUploadListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MediaUploadListener listener) {
        listeners.remove(listener);
    }

    public void start(
        String id,
        boolean downsize,
        @Nullable String draftId,
        @Nullable String clientId,
        Runnable afterScheduling
    ) {
        commandExecutor.execute(() -> {
            try {
                startOrdered(id, downsize, draftId, clientId);
            } finally {
                afterScheduling.run();
            }
        });
    }

    public void assignToDraft(String draftId) {
        commandExecutor.execute(() -> {
            String homeLocation = currentHomeLocation();
            if (homeLocation == null) {
                return;
            }
            List<String> ids;
            synchronized (databaseLock) {
                ids = dao.assignUnassignedAndReturnIds(draftId, homeLocation, System.currentTimeMillis());
            }
            for (String id : ids) {
                MediaUploadEntity upload = get(id);
                if (upload != null) {
                    emitState(upload);
                }
            }
        });
    }

    public void requestStates() {
        commandExecutor.execute(() -> {
            String homeLocation = currentHomeLocation();
            if (homeLocation == null) {
                return;
            }
            List<MediaUploadEntity> uploads;
            synchronized (databaseLock) {
                uploads = dao.getRetained(homeLocation);
            }
            uploads.forEach(this::emitState);
        });
    }

    public void cancel(String id) {
        cancelled.add(id);
        api.cancel(id);
        Thread thread = activeThreads.get(id);
        if (thread != null) {
            thread.interrupt();
        }
        commandExecutor.execute(() -> {
            MediaUploadEntity upload = get(id);
            if (upload != null) {
                scheduler.cancel(upload);
            }
            cleanupCanceled(id);
        });
    }

    public void discardSelected(String id) {
        commandExecutor.execute(() -> {
            if (get(id) != null) {
                cancel(id);
            } else {
                selectedMediaStore.discard(id);
            }
        });
    }

    public void acknowledge(String id) {
        commandExecutor.execute(() -> {
            synchronized (databaseLock) {
                MediaUploadEntity upload = dao.get(id);
                if (upload != null && Objects.equals(upload.state, MediaUploadState.COMPLETED.name())) {
                    dao.delete(id);
                    MediaUploadNotifications.cancel(context, id);
                }
            }
        });
    }

    public void abandonDraft(String draftId) {
        commandExecutor.execute(() -> {
            List<MediaUploadEntity> uploads;
            synchronized (databaseLock) {
                uploads = dao.getByDraft(draftId);
            }
            for (MediaUploadEntity upload : uploads) {
                if (Objects.equals(upload.state, MediaUploadState.COMPLETED.name())) {
                    synchronized (databaseLock) {
                        dao.delete(upload.mediaId);
                    }
                    MediaUploadNotifications.cancel(context, upload.mediaId);
                } else {
                    cancel(upload.mediaId);
                }
            }
        });
    }

    public void cleanupExpiredSelections(long timestamp) {
        commandExecutor.execute(() -> selectedMediaStore.discardOlderThan(timestamp));
    }

    public void reconcileScheduledUploads() {
        commandExecutor.execute(this::reconcileScheduledUploadsOrdered);
    }

    public void runScheduled(String id) {
        if (stoppedRuns.remove(id)) {
            return;
        }
        if (!activeRuns.add(id)) {
            return;
        }
        activeThreads.put(id, Thread.currentThread());
        try {
            new MediaUploadRunner(this, context, api, objectMapper).run(id);
        } finally {
            activeThreads.remove(id, Thread.currentThread());
            activeRuns.remove(id);
            stoppedRuns.remove(id);
            if (get(id) == null) {
                cancelled.remove(id);
            }
        }
    }

    public void stopScheduledRun(String id) {
        stoppedRuns.add(id);
        api.cancel(id);
        Thread thread = activeThreads.get(id);
        if (thread != null) {
            thread.interrupt();
        }
    }

    public boolean isResumable(String id) {
        MediaUploadEntity upload = get(id);
        return upload != null && isResumable(upload);
    }

    @Nullable
    public MediaUploadEntity getUpload(String id) {
        return get(id);
    }

    public void clearJobId(String id, int jobId) {
        synchronized (databaseLock) {
            dao.clearJobId(id, jobId);
        }
    }

    public void clearWorkId(String id, String workId) {
        synchronized (databaseLock) {
            dao.clearWorkId(id, workId);
        }
    }

    private void startOrdered(
        String id, boolean downsize, @Nullable String draftId, @Nullable String clientId
    ) {
        MediaUploadEntity existing = get(id);
        if (existing != null) {
            if (existing.draftId != null && draftId != null && !existing.draftId.equals(draftId)) {
                emitTransientFailure(id, draftId, "draft-mismatch", "Media upload belongs to another draft");
                return;
            }
            if (existing.draftId == null && draftId != null) {
                synchronized (databaseLock) {
                    dao.assignOneIfUnassigned(id, draftId, System.currentTimeMillis());
                    existing = dao.get(id);
                }
            }
            if (Objects.equals(existing.state, MediaUploadState.FAILED.name()) && existing.retryable) {
                existing.state = MediaUploadState.QUEUED.name();
                clearError(existing);
                save(existing);
                emitState(existing);
                schedule(existing);
            } else {
                emitState(existing);
                if (!isTerminal(existing)) {
                    schedule(existing);
                }
            }
            return;
        }

        SelectedMedia selectedMedia = selectedMediaStore.get(id);
        if (selectedMedia == null) {
            emitTransientFailure(id, draftId, "source-missing", "Selected media is no longer available");
            return;
        }
        String homeLocation = currentHomeLocation();
        String token = currentToken();
        if (homeLocation == null || homeLocation.isBlank()) {
            emitTransientFailure(id, draftId, "home-required", "Home node is not connected");
            return;
        }
        if (token == null || token.isBlank()) {
            emitTransientFailure(id, draftId, "authentication-required", "Authentication is required");
            return;
        }

        long now = System.currentTimeMillis();
        boolean mediaDownsize = downsize
            && (MimeUtil.isImageType(selectedMedia.mimeType()) || MimeUtil.isVideoType(selectedMedia.mimeType()));
        MediaUploadEntity upload = new MediaUploadEntity(
            id,
            draftId,
            homeLocation,
            clientId,
            selectedMedia.displayName(),
            selectedMedia.mimeType(),
            selectedMedia.size(),
            readThumbnail(selectedMedia.thumbnailPath()),
            MediaUploadState.QUEUED.name(),
            mediaDownsize,
            now,
            now
        );
        synchronized (databaseLock) {
            dao.insert(upload);
        }
        emitState(upload);
        schedule(upload);
    }

    private void schedule(MediaUploadEntity upload) {
        if (scheduler.isScheduled(upload)) {
            return;
        }
        synchronized (databaseLock) {
            MediaUploadEntity current = dao.get(upload.mediaId);
            if (current == null || isTerminal(current)) {
                return;
            }
            if (MediaUploadScheduler.usesJobs(android.os.Build.VERSION.SDK_INT)) {
                current.jobId = allocateJobId(current.mediaId);
                current.workId = null;
            } else {
                current.jobId = null;
                current.workId = MediaUploadScheduler.newWorkId();
            }
            current.updatedAt = System.currentTimeMillis();
            dao.update(current);
            upload = current;
        }
        cancelled.remove(upload.mediaId);
        try {
            if (!scheduler.schedule(upload)) {
                fail(
                    upload,
                    new MediaUploadFailure(
                        "scheduler-unavailable", "Cannot schedule media upload", true
                    )
                );
            }
        } catch (RuntimeException e) {
            fail(
                upload,
                new MediaUploadFailure(
                    "scheduler-unavailable", "Cannot schedule media upload", true, false, e
                )
            );
        }
    }

    private int allocateJobId(String mediaId) {
        int jobId = MediaUploadScheduler.jobId(mediaId);
        while (true) {
            MediaUploadEntity owner = dao.getByJobId(jobId);
            if (owner == null || owner.mediaId.equals(mediaId)) {
                return jobId;
            }
            jobId = jobId == 0x6fffffff ? 0x60000000 : jobId + 1;
        }
    }

    private void cleanupCanceled(String id) {
        MediaUploadEntity upload = get(id);
        if (upload != null && upload.serverUploadId != null) {
            try {
                api.delete(credentials(upload), upload.serverUploadId);
            } catch (Exception ignored) {
                // Server-side temporary data expires even if best-effort deletion fails.
            }
        }
        selectedMediaStore.discard(id);
        synchronized (databaseLock) {
            dao.delete(id);
        }
        lastProgressAt.remove(id);
        MediaUploadNotifications.cancel(context, id);
    }

    private void reconcileScheduledUploadsOrdered() {
        String homeLocation = currentHomeLocation();
        if (homeLocation == null) {
            return;
        }
        List<MediaUploadEntity> finalizing;
        List<MediaUploadEntity> resumable;
        synchronized (databaseLock) {
            finalizing = dao.getFinalizing(homeLocation);
            resumable = dao.getResumable(homeLocation);
        }
        for (MediaUploadEntity upload : finalizing) {
            fail(
                upload,
                new MediaUploadFailure(
                    "completion-unknown",
                    "Cannot determine whether media finalization completed",
                    false,
                    true,
                    null
                )
            );
        }
        for (MediaUploadEntity upload : resumable) {
            if (!scheduler.isScheduled(upload)) {
                schedule(upload);
            }
        }
    }

    @Nullable
    MediaUploadEntity get(String id) {
        synchronized (databaseLock) {
            return dao.get(id);
        }
    }

    void save(MediaUploadEntity upload) {
        synchronized (databaseLock) {
            MediaUploadEntity current = dao.get(upload.mediaId);
            if (current == null) {
                return;
            }
            upload.draftId = current.draftId;
            upload.updatedAt = System.currentTimeMillis();
            dao.update(upload);
        }
    }

    void complete(MediaUploadEntity upload) {
        save(upload);
        try {
            selectedMediaStore.discard(upload.mediaId);
        } catch (RuntimeException ignored) {
            // Completion is already durable; source cleanup can be retried independently.
        }
        MediaUploadNotifications.showResult(context, upload);
        emitCompleted(upload);
    }

    MediaUploadEntity applyServerInfo(String id, MediaUploadInfo info, boolean authoritative)
        throws MediaUploadFailure {
        synchronized (databaseLock) {
            MediaUploadEntity upload = dao.get(id);
            if (upload == null) {
                throw new MediaUploadFailure("cancelled", "Media upload was cancelled", false);
            }
            if (
                info.getChunkSize() <= 0
                || info.getFileSize() != upload.size
                || info.getDeadline() <= 0
                || info.getUploadedChunks() == null
                || upload.serverChunkSize != null && upload.serverChunkSize != info.getChunkSize()
            ) {
                throw new MediaUploadFailure("invalid-response", "Node returned inconsistent upload metadata", false);
            }
            if (
                info.getId() == null
                || info.getId().isBlank()
                || upload.serverUploadId != null && !upload.serverUploadId.equals(info.getId())
            ) {
                throw new MediaUploadFailure("invalid-response", "Node returned an inconsistent upload ID", false);
            }
            Set<Integer> chunks = authoritative ? new LinkedHashSet<>() : readUploadedChunks(upload.uploadedChunksJson);
            if (info.getUploadedChunks() != null) {
                chunks.addAll(info.getUploadedChunks());
            }
            long confirmedBytes = getConfirmedBytes(info, upload, chunks);
            try {
                upload.uploadedChunksJson = objectMapper.writeValueAsString(chunks);
            } catch (JacksonException e) {
                throw new MediaUploadFailure("storage", "Cannot store upload progress", false, false, e);
            }
            upload.serverUploadId = info.getId();
            upload.serverChunkSize = info.getChunkSize();
            upload.serverDeadline = info.getDeadline();
            upload.confirmedBytes = confirmedBytes;
            upload.state = MediaUploadState.UPLOADING.name();
            upload.updatedAt = System.currentTimeMillis();
            dao.update(upload);
            return upload;
        }
    }

    private static long getConfirmedBytes(
        MediaUploadInfo info,
        MediaUploadEntity upload,
        Set<Integer> chunks
    ) throws MediaUploadFailure {
        int chunkCount = (int) ((upload.size + info.getChunkSize() - 1) / info.getChunkSize());

        long confirmedBytes = 0;
        for (int chunk : chunks) {
            if (chunk < 0 || chunk >= chunkCount) {
                throw new MediaUploadFailure("invalid-response", "Node returned an invalid chunk number", false);
            }
            confirmedBytes += Math.min(info.getChunkSize(), upload.size - (long) chunk * info.getChunkSize());
        }

        if (info.getCompletedAt() != null && confirmedBytes != upload.size) {
            throw new MediaUploadFailure(
                "invalid-response", "Node completed an upload with missing chunks", false
            );
        }

        return confirmedBytes;
    }

    Set<Integer> readUploadedChunks(String json) throws MediaUploadFailure {
        try {
            List<Integer> chunks = objectMapper.readValue(json, new TypeReference<List<Integer>>() {
            });
            return new LinkedHashSet<>(chunks);
        } catch (JacksonException e) {
            throw new MediaUploadFailure("storage", "Stored upload progress is invalid", false, false, e);
        }
    }

    MediaUploadEntity updateState(String id, MediaUploadState state) throws MediaUploadFailure {
        synchronized (databaseLock) {
            MediaUploadEntity upload = dao.get(id);
            if (upload == null) {
                throw new MediaUploadFailure("cancelled", "Media upload was cancelled", false);
            }
            upload.state = state.name();
            upload.updatedAt = System.currentTimeMillis();
            dao.update(upload);
            return upload;
        }
    }

    void fail(MediaUploadEntity upload, MediaUploadFailure failure) {
        if (isCancelled(upload.mediaId)) {
            cleanupCanceled(upload.mediaId);
            return;
        }
        if (stoppedRuns.contains(upload.mediaId)) {
            MediaUploadEntity current = get(upload.mediaId);
            if (current == null) {
                return;
            }
            if (!Objects.equals(current.state, MediaUploadState.FINALIZING.name())) {
                pause(upload.mediaId);
                return;
            }
            failure = new MediaUploadFailure(
                "completion-unknown",
                "Cannot determine whether media finalization completed",
                false,
                true,
                failure
            );
        }
        MediaUploadEntity current;
        synchronized (databaseLock) {
            current = dao.get(upload.mediaId);
            if (current == null) {
                return;
            }
            current.state = MediaUploadState.FAILED.name();
            current.lastErrorCode = failure.getCode();
            current.lastErrorMessage = failure.getMessage();
            current.retryable = failure.isRetryable();
            current.completionUnknown = failure.isCompletionUnknown();
            current.updatedAt = System.currentTimeMillis();
            dao.update(current);
        }
        MediaUploadNotifications.showResult(context, current);
        emitFailed(current);
    }

    private void pause(String id) {
        MediaUploadEntity upload;
        synchronized (databaseLock) {
            upload = dao.get(id);
            if (upload == null || isTerminal(upload)) {
                return;
            }
            upload.state = upload.serverUploadId == null
                ? MediaUploadState.QUEUED.name() : MediaUploadState.UPLOADING.name();
            upload.updatedAt = System.currentTimeMillis();
            dao.update(upload);
        }
        emitState(upload);
    }

    MediaUploadApi.Credentials credentials(MediaUploadEntity upload) throws MediaUploadFailure {
        String homeLocation = currentHomeLocation();
        if (!Objects.equals(homeLocation, upload.homeLocation)) {
            throw new MediaUploadFailure("home-changed", "Home node changed during media upload", false);
        }
        String token = currentToken();
        if (token == null || token.isBlank()) {
            throw new MediaUploadFailure("authentication-required", "Authentication is required", false);
        }
        return new MediaUploadApi.Credentials(upload.homeLocation, token, upload.clientId, upload.mediaId);
    }

    boolean isCancelled(String id) {
        return cancelled.contains(id);
    }

    boolean isStopped(String id) {
        return stoppedRuns.contains(id);
    }

    boolean isCurrentHome(String homeLocation) {
        return Objects.equals(homeLocation, currentHomeLocation());
    }

    SelectedMedia selectedMedia(String id) {
        return selectedMediaStore.get(id);
    }

    void emitState(MediaUploadEntity upload) {
        listeners.forEach(listener -> listener.onState(upload));
    }

    void emitProgress(MediaUploadEntity upload) {
        long now = System.currentTimeMillis();
        Long previous = lastProgressAt.get(upload.mediaId);
        if (upload.confirmedBytes < upload.size && previous != null && now - previous < 250) {
            return;
        }
        lastProgressAt.put(upload.mediaId, now);
        listeners.forEach(listener -> listener.onProgress(upload));
    }

    private void emitCompleted(MediaUploadEntity upload) {
        listeners.forEach(listener -> listener.onCompleted(upload));
    }

    private void emitFailed(MediaUploadEntity upload) {
        listeners.forEach(listener -> listener.onFailed(upload));
    }

    private void emitTransientFailure(String id, @Nullable String draftId, String code, String message) {
        listeners.forEach(listener -> listener.onTransientFailure(id, draftId, code, message));
    }

    private String currentHomeLocation() {
        return preferences().getString(Preferences.HOME_LOCATION, null);
    }

    private String currentToken() {
        return preferences().getString(Preferences.HOME_TOKEN, null);
    }

    private SharedPreferences preferences() {
        return context.getSharedPreferences(Preferences.GLOBAL, MODE_PRIVATE);
    }

    private static boolean isTerminal(MediaUploadEntity upload) {
        return Objects.equals(upload.state, MediaUploadState.COMPLETED.name())
            || Objects.equals(upload.state, MediaUploadState.CANCELED.name())
            || Objects.equals(upload.state, MediaUploadState.FAILED.name());
    }

    private static boolean isResumable(MediaUploadEntity upload) {
        return Objects.equals(upload.state, MediaUploadState.QUEUED.name())
            || Objects.equals(upload.state, MediaUploadState.CREATING.name())
            || Objects.equals(upload.state, MediaUploadState.UPLOADING.name())
            || Objects.equals(upload.state, MediaUploadState.RETRY_WAIT.name());
    }

    private static void clearError(MediaUploadEntity upload) {
        upload.lastErrorCode = null;
        upload.lastErrorMessage = null;
        upload.retryable = false;
        upload.completionUnknown = false;
    }

    @Nullable
    private static String readThumbnail(@Nullable String path) {
        if (path == null) {
            return null;
        }
        try {
            File file = new File(path);
            if (!file.isFile() || file.length() > MAX_THUMBNAIL_BYTES) {
                return null;
            }
            String mimeType = path.endsWith(".png") ? "image/png" : "image/jpeg";
            return "data:" + mimeType + ";base64,"
                + Base64.encodeToString(Files.readAllBytes(file.toPath()), Base64.NO_WRAP);
        } catch (IOException e) {
            return null;
        }
    }

}
