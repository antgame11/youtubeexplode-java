package youtubeexplode.bridge.cipher;

public interface CipherOperation {
    String decipher(String input);

    record Reverse() implements CipherOperation {
        @Override
        public String decipher(String input) {
            return new StringBuilder(input).reverse().toString();
        }

        @Override
        public String toString() {
            return "Reverse";
        }
    }

    record Splice(int index) implements CipherOperation {
        @Override
        public String decipher(String input) {
            return input.substring(index);
        }

        @Override
        public String toString() {
            return "Splice (" + index + ")";
        }
    }

    record Swap(int index) implements CipherOperation {
        @Override
        public String decipher(String input) {
            StringBuilder sb = new StringBuilder(input);
            sb.setCharAt(0, input.charAt(index));
            sb.setCharAt(index, input.charAt(0));
            return sb.toString();
        }

        @Override
        public String toString() {
            return "Swap (" + index + ")";
        }
    }
}
