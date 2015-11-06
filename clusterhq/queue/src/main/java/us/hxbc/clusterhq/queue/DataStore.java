package us.hxbc.clusterhq.queue;

import com.google.common.io.ByteStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.StreamCorruptedException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static java.util.Objects.requireNonNull;

public class DataStore {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private Path dir;
    private final long CHUNK_SIZE;
    private long nextLSN;

    DataStore(Path dir, long chunkSize) {
        this.dir = requireNonNull(dir);
        if ((chunkSize & (chunkSize - 1)) != 0) {
            throw new IllegalArgumentException(chunkSize + " is not power of 2");
        }
        this.CHUNK_SIZE = chunkSize;
    }

    long getBaseLSN(long lsn) {
        return lsn & ~(CHUNK_SIZE - 1);
    }

    Path getChunkPath(long baseLSN) {
        String name = Long.toHexString(baseLSN);
        return dir.resolve(name);
    }

    public synchronized long post(InputStream data) throws IOException {
        long baseLSN = getBaseLSN(nextLSN);
        Path chunk = getChunkPath(baseLSN);

        logger.debug("Writing to {}", chunk);
        long origSize = -1;
        FileChannel out = null;
        try {
            out = FileChannel.open(chunk, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            origSize = out.size();
            out.position(origSize);
            int nread;
            long total = 0;
            ByteBuffer buf = ByteBuffer.allocate(32 * 1024);
            buf.mark();
            buf.putLong(0); // placeholder for size of this message
            buf.limit(buf.position());
            buf.reset();
            int nwritten = out.write(buf);
            if (nwritten != 8) {
                throw new IllegalArgumentException(nwritten + " != 8");
            }
            buf.reset();
            buf.limit(buf.capacity());
            while ((nread = data.read(buf.array())) > 0) {
                buf.limit(nread);
                nwritten = out.write(buf);
                if (nwritten != nread) {
                    throw new IllegalArgumentException(nwritten + " != " + nread);
                }
                buf.reset();
                total += nread;
            }
            buf.reset();
            buf.limit(buf.capacity());
            buf.putLong(total);
            buf.limit(buf.position());
            buf.reset();
            nwritten = out.write(buf, origSize); // write the real size
            if (nwritten != 8) {
                throw new IllegalArgumentException(nwritten + " != 8");
            }
            out.force(true);
            nextLSN += total + 8;
            if (getBaseLSN(nextLSN) != baseLSN) {
                // we exceeded this chunk, round this up to the next chunk
                nextLSN = getBaseLSN(nextLSN) + CHUNK_SIZE;
            }
        } catch (IOException e) {
            // truncate the file back to the original size
            if (out != null && origSize != -1) {
                out.truncate(origSize);
                out.force(true);
            }
            throw e;
        } finally {
            if (out != null) {
                out.close();
            }
        }

        return nextLSN;
    }

    public static class Message {
        public final InputStream in;
        public final long nextLSN;

        Message(InputStream in, long nextLSN) {
            this.in = requireNonNull(in);
            this.nextLSN = nextLSN;
        }
    }

    public Message get(long lsn) throws IOException {
        long baseLSN = getBaseLSN(lsn);
        Path chunk = getChunkPath(baseLSN);
        long relativeLSN = lsn - baseLSN;
        long chunkSize;
        if (Files.notExists(chunk) || (chunkSize = Files.size(chunk)) < relativeLSN) {
            throw new ClientErrorException(Response.Status.NOT_FOUND);
        }

        FileChannel in = null;
        try {
            in = FileChannel.open(chunk, StandardOpenOption.READ);
            logger.debug("seeking to {}/{} in {}", relativeLSN, baseLSN, in.size());
            if (in.size() < relativeLSN) {
                in.close();
                throw new ClientErrorException(Response.Status.NOT_FOUND);
            }
            in.position(relativeLSN);
            ByteBuffer buf = ByteBuffer.allocate(8);
            buf.mark();
            in.read(buf);
            buf.reset();
            long messageSize = buf.getLong();
            logger.debug("message is {} bytes", messageSize);
            if (chunkSize < relativeLSN + 8 + messageSize) {
                in.close();
                throw new StreamCorruptedException(
                        String.format("%s/%s is %s bytes but chunk is %s bytes", relativeLSN, lsn, messageSize, chunkSize));
            }

            long nextLSN = lsn + 8 + messageSize;
            if (getBaseLSN(nextLSN) != baseLSN) {
                // we exceeded this chunk, round this up to the next chunk
                nextLSN = getBaseLSN(nextLSN) + CHUNK_SIZE;
            }
            return new Message(ByteStreams.limit(Channels.newInputStream(in), messageSize), nextLSN);
        } catch (BufferUnderflowException e) {
            throw new IOException(e);
        } catch (IOException e) {
            if (in != null) {
                in.close();
            }
            throw e;
        } catch (RuntimeException e) {
            if (in != null) {
                in.close();
            }
            throw e;
        }
    }
}
