import java.io.IOException;
import okio.Buffer;
import okio.ForwardingSink;
import okio.Okio;

public final class Okiofix1PublishedNullableUse {
  public static void main(String[] arguments) {
    int[] closes = {0};
    ForwardingSink sink = new ForwardingSink(new Buffer()) {
      @Override public void close() throws IOException {
        closes[0]++;
        super.close();
      }
    };
    String result = Okio.use(sink, value -> null);
    if (result != null || closes[0] != 1) {
      throw new AssertionError("Expected null and one close, got " + result + " and " + closes[0]);
    }
    System.out.println("Published public Okio.use returns null; close count=1");
  }
}
