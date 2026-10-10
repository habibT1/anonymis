package com.inf5190.chat.messages.repository;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.Query.Direction;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Bucket.BlobTargetOption;

import com.google.firebase.cloud.StorageClient;

import com.inf5190.chat.messages.model.Message;
import com.inf5190.chat.messages.model.NewMessageRequest;

@Repository
public class MessageRepository {

    private static final String COLLECTION_NAME = "messages";

    private final Firestore firestore;
    private final StorageClient storageClient;

    @Value("${firebase.storage.bucket.name}")
    private String bucketName;

    public MessageRepository(Firestore firestore, StorageClient storageClient) {
        this.firestore = firestore;
        this.storageClient = storageClient;
    }


    private Message toMessageOrNull(DocumentSnapshot document) {
        String id       = document.getId();
        String username = document.getString("username");
        String text     = document.getString("text");
        String imageUrl = document.getString("imageUrl");

        Object rawTs = document.get("timestamp");

        Long timestampMillis = null;

        if (rawTs instanceof Timestamp ts) {
            timestampMillis = ts.toDate().getTime();
        } else if (rawTs instanceof Long l) {

            timestampMillis = l;
        }

        if (username == null || text == null || timestampMillis == null) {
            return null;
        }

        return new Message(
                id,
                text,
                username,
                timestampMillis,
                imageUrl
        );
    }


    public List<Message> getMessages(String fromId)
            throws InterruptedException, ExecutionException {

        CollectionReference messagesCollection =
                firestore.collection(COLLECTION_NAME);


        if (fromId == null || fromId.isBlank()) {

            Query query = messagesCollection
                    .orderBy("timestamp", Direction.DESCENDING)
                    .limit(20);

            ApiFuture<QuerySnapshot> future = query.get();
            List<QueryDocumentSnapshot> docsDesc = future.get().getDocuments();

            List<Message> temp = new ArrayList<>();
            for (DocumentSnapshot doc : docsDesc) {
                Message m = toMessageOrNull(doc);
                if (m != null) {
                    temp.add(m);
                }
            }


            List<Message> result = new ArrayList<>();
            for (int i = temp.size() - 1; i >= 0; i--) {
                result.add(temp.get(i));
            }

            return result;
        }


        DocumentSnapshot startAfterDoc =
                messagesCollection.document(fromId).get().get();

        if (!startAfterDoc.exists()) {
            return new ArrayList<>();
        }

        Query query = messagesCollection
                .orderBy("timestamp", Direction.ASCENDING)
                .startAfter(startAfterDoc);

        ApiFuture<QuerySnapshot> future = query.get();
        List<QueryDocumentSnapshot> docsAsc = future.get().getDocuments();

        List<Message> result = new ArrayList<>();
        for (DocumentSnapshot doc : docsAsc) {
            Message m = toMessageOrNull(doc);
            if (m != null) {
                result.add(m);
            }
        }

        return result;
    }


    public Message createMessage(NewMessageRequest req)
            throws InterruptedException, ExecutionException {

        CollectionReference messagesCollection =
                firestore.collection(COLLECTION_NAME);

        String docId = messagesCollection.document().getId();

        long nowMillis = System.currentTimeMillis();

        String imageUrl = null;

        if (req.imageData() != null) {
            String ext = req.imageData().type();

            String objectPath = String.format("images/%s.%s", docId, ext);

            Bucket bucket = this.storageClient.bucket(this.bucketName);

            byte[] decodedBytes = Base64.getDecoder()
                    .decode(req.imageData().data());

            bucket.create(
                    objectPath,
                    decodedBytes,
                    "image/" + ext,
                    BlobTargetOption.predefinedAcl(Storage.PredefinedAcl.PUBLIC_READ)
            );

            imageUrl = String.format(
                    "https://storage.googleapis.com/%s/%s",
                    this.bucketName,
                    objectPath
            );
        }

        Timestamp firestoreTs = Timestamp.ofTimeSecondsAndNanos(
                nowMillis / 1000,
                (int) ((nowMillis % 1000) * 1_000_000)
        );

        FirestoreMessage fsMsg = new FirestoreMessage(
                req.username(),
                firestoreTs,
                req.text(),
                imageUrl
        );

        messagesCollection.document(docId).set(fsMsg).get();

        return new Message(
                docId,
                req.text(),
                req.username(),
                nowMillis,
                imageUrl
        );
    }
}