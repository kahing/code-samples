package us.hxbc.clusterhq.queue;

import org.glassfish.grizzly.http.server.Request;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class Api {
    private final java.nio.file.Path dir;
    private final long CHUNK_SIZE;
    private Map<String, Queue> topics = new HashMap<>();

    Api(java.nio.file.Path dir, long chunkSize) {
        this.dir = requireNonNull(dir);
        this.CHUNK_SIZE = chunkSize;
    }

    @Path("/{topic}/{username}")
    @POST
    public void subscribe(@PathParam("topic") String topic,
                          @PathParam("username") String username) throws IOException {
        ensureTopic(topic).subscribe(username);
    }

    private Queue ensureTopic(@PathParam("topic") String topic) throws IOException {
        Queue q;
        synchronized (topics) {
            q = topics.get(topic);
            if (q == null) {
                q = new Queue(dir, CHUNK_SIZE);
                topics.put(topic, q);
            }
        }
        return q;
    }

    @Path("/{topic}/{username}")
    @DELETE
    public void unsubscribe(@PathParam("topic") String topic,
                            @PathParam("username") String username) throws IOException {
        Queue q;
        synchronized (topics) {
            q = topics.get(topic);
        }

        if (q == null) {
            throw new ClientErrorException(Response.Status.NOT_FOUND);
        } else {
            q.unsubscribe(username);
        }
    }

    @Path("/{topic}")
    @POST
    public void publish(@PathParam("topic") String topic,
                        @Context Request request) throws IOException {
        ensureTopic(topic).post(request.getInputStream());
        request.getInputStream().close();
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
