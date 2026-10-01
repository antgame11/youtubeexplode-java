package youtubeexplode.bridge.cipher;

import java.util.List;

public final class CipherManifest {
    private final String signatureTimestamp;
    private final List<CipherOperation> operations;

    public CipherManifest(String signatureTimestamp, List<CipherOperation> operations) {
        this.signatureTimestamp = signatureTimestamp;
        this.operations = List.copyOf(operations);
    }

    public String signatureTimestamp() {
        return signatureTimestamp;
    }

    public List<CipherOperation> operations() {
        return operations;
    }

    public String decipher(String input) {
        String result = input;
        for (CipherOperation op : operations) result = op.decipher(result);
        return result;
    }
}
