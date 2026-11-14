import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Prints the public route-55 alert selectors from a captured adapter feed. */
public class InspectStmFeed {
    public static void main(String[] args) throws Exception {
        var feed = FeedMessage.parseFrom(Files.readAllBytes(Path.of(args[0])));
        System.out.println("Timestamp=" + feed.getHeader().getTimestamp() + ", entities=" + feed.getEntityCount());
        for (var entity : feed.getEntityList()) {
            if (entity.hasAlert() && entity.getAlert().getInformedEntityList().stream()
                .anyMatch(selector -> selector.getRouteId().equals("55"))) {
                System.out.println(entity);
            }
        }
    }
}
