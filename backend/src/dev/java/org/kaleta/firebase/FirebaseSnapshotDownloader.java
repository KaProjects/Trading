package org.kaleta.firebase;

import java.util.Map;

@FunctionalInterface
public interface FirebaseSnapshotDownloader
{
    Map<String, Object> download();
}
