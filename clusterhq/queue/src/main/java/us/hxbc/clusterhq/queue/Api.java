package us.hxbc.clusterhq.queue;

import org.glassfish.grizzly.http.server.Request;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Throwables.propagate;
import static java.util.Objects.requireNonNull;

@Path("/")
public class Api {
    private final java.nio.file.Path dir;
    private final long CHUNK_SIZE;
    private Map<String, Queue> topics = new HashMap<>();

    Api(java.nio.file.Path dir, long chunkSize) throws IOException {
        this.dir = requireNonNull(dir);
        this.CHUNK_SIZE = chunkSize;
        init();
    }

    private void init() throws IOException {
        Files.list(dir)
                .filter(p -> Files.isDirectory(p.resolve("data")) &&
                        Files.isDirectory(p.resolve("subscriptions")))
                .forEach(p -> {
                    try {
                        String name = p.getFileName().toString();
                        Queue q = new Queue(p, CHUNK_SIZE);
                        topics.put(name, q);
                        q.spawnGCThread();
                    } catch (IOException e) {
                        throw propagate(e);
                    }
                });
    }

    public void stop() {
        synchronized (topics) {
            topics.values().forEach(q -> q.stop());
        }
    }

    @Path("/{topic}/{username}")
    @POST
    public Response subscribe(@PathParam("topic") String topic,
                          @PathParam("username") String username) throws IOException {
        ensureTopic(topic).subscribe(username);
        return Response.ok().build();
    }

    private Queue ensureTopic(@PathParam("topic") String topic) throws IOException {
        Queue q;
        synchronized (topics) {
            q = topics.get(topic);
            if (q == null) {
                java.nio.file.Path p = dir.resolve(topic);
                Files.createDirectory(p);
                q = new Queue(p, CHUNK_SIZE);
                topics.put(topic, q);
                q.spawnGCThread();
            }
        }
        return q;
    }

    @Path("/{topic}/{username}")
    @DELETE
    public Response unsubscribe(@PathParam("topic") String topic,
                            @PathParam("username") String username) throws IOException {
        Queue q;
        synchronized (topics) {
            q = topics.get(topic);
        }

        if (q == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } else {
            q.unsubscribe(username);
            return Response.ok().build();
        }
    }

    @Path("/{topic}")
    @POST
    public Response publish(@PathParam("topic") String topic,
                        @Context Request request) throws IOException {
        ensureTopic(topic).post(request.getInputStream());
        request.getInputStream().close();
        return Response.ok().build();
    }

    @Path("/{topic}/{username}")
    @GET
    public Response get(@PathParam("topic") String topic,
                        @PathParam("username") String username) throws IOException {
        Queue q;
        synchronized (topics) {
            q = topics.get(topic);
        }

        if (q == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } else {
            DataStore.Message m = q.get(username);
            if (m.in == null) {
                return Response.noContent().build();
            } else {
                return Response.ok(m.in).build();
            }
        }
    }
}
