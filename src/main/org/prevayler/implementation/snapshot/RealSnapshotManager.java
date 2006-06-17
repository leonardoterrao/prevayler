package org.prevayler.implementation.snapshot;

import org.prevayler.foundation.serialization.Serializer;
import org.prevayler.implementation.PrevalentSystemGuard;
import org.prevayler.implementation.PrevaylerDirectory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;

public class RealSnapshotManager implements SnapshotManager {

    private Map _strategies;

    private String _primarySuffix;

    private PrevaylerDirectory _directory;

    private PrevalentSystemGuard _recoveredPrevalentSystem;

    public RealSnapshotManager(Map snapshotSerializers, String primarySnapshotSuffix, Object newPrevalentSystem, PrevaylerDirectory directory, Serializer journalSerializer) {
        for (Iterator iterator = snapshotSerializers.keySet().iterator(); iterator.hasNext();) {
            String suffix = (String) iterator.next();
            PrevaylerDirectory.checkValidSnapshotSuffix(suffix);
        }

        if (!snapshotSerializers.containsKey(primarySnapshotSuffix)) {
            throw new IllegalArgumentException("Primary suffix '" + primarySnapshotSuffix + "' does not appear in strategies map");
        }

        try {
            _strategies = snapshotSerializers;
            _primarySuffix = primarySnapshotSuffix;

            _directory = directory;
            _directory.produceDirectory();

            File latestSnapshot = _directory.latestSnapshot();
            long recoveredVersion = latestSnapshot == null ? 0 : PrevaylerDirectory.snapshotVersion(latestSnapshot);
            Object recoveredPrevalentSystem = latestSnapshot == null ? newPrevalentSystem : readSnapshot(latestSnapshot);
            _recoveredPrevalentSystem = new PrevalentSystemGuard(recoveredPrevalentSystem, recoveredVersion, journalSerializer);
        } catch (Exception e) {
            throw new SnapshotError(e);
        }
    }

    private Object readSnapshot(File snapshotFile) throws Exception {
        String suffix = snapshotFile.getName().substring(snapshotFile.getName().indexOf('.') + 1);
        if (!_strategies.containsKey(suffix)) {
            throw new SnapshotError(snapshotFile.toString() + " cannot be read; only " + _strategies.keySet().toString() + " supported");
        }

        Serializer serializer = (Serializer) _strategies.get(suffix);
        FileInputStream in = new FileInputStream(snapshotFile);
        try {
            return serializer.readObject(in);
        } finally {
            in.close();
        }
    }

    public Serializer primarySerializer() {
        return (Serializer) _strategies.get(_primarySuffix);
    }

    public PrevalentSystemGuard recoveredPrevalentSystem() {
        return _recoveredPrevalentSystem;
    }

    public void writeSnapshot(Object prevalentSystem, long version) {
        try {
            File tempFile = _directory.createTempFile("snapshot" + version + "temp", "generatingSnapshot");

            OutputStream out = new FileOutputStream(tempFile);
            try {
                primarySerializer().writeObject(out, prevalentSystem);
            } finally {
                out.close();
            }

            File permanent = snapshotFile(version);
            permanent.delete();
            if (!tempFile.renameTo(permanent)) {
                throw new SnapshotError("Temporary snapshot file generated: " + tempFile + "\nUnable to rename it permanently to: " + permanent);
            }
        } catch (Exception e) {
            throw new SnapshotError(e);
        }
    }

    private File snapshotFile(long version) {
        return _directory.snapshotFile(version, _primarySuffix);
    }

}
