package ru.mail.polis.service.yaroslav;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import one.nio.http.HttpServer;
import one.nio.http.Response;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpClient;
import one.nio.http.HttpSession;
import one.nio.http.Request;

import one.nio.http.Path;
import one.nio.net.ConnectionString;
import one.nio.net.Socket;
import one.nio.server.AcceptorConfig;
import org.jetbrains.annotations.NotNull;
import ru.mail.polis.Record;
import ru.mail.polis.dao.DAO;
import ru.mail.polis.dao.DAOImpl;
import ru.mail.polis.dao.NoSuchElementExceptionLite;
import ru.mail.polis.service.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

public class ServiceImpl extends HttpServer implements Service {
    @NotNull
    private final DAOImpl dao;
    @NotNull
    private final Executor executor;
    private final Node node;
    private final Map<String, HttpClient> clusterClients;
    private final int clusterSize;
    private static final Logger logger = Logger.getLogger(ServiceImpl.class.getName());
    private static final String PROXY_HEADER = "X-OK-Proxy: True";
    private final RF defaultRF;

    /**
     * Async Service.
     */
    private ServiceImpl(final HttpServerConfig config, @NotNull final DAO dao,
                        @NotNull final Node node,
                        @NotNull final Map<String, HttpClient> clusterClients) throws IOException {
        super(config);
        this.dao = (DAOImpl) dao;
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(),
                new ThreadFactoryBuilder().setNameFormat("worker-%d").build());
        this.node = node;
        this.clusterClients = clusterClients;
        this.defaultRF = new RF(node.getNodes().size() / 2 + 1, node.getNodes().size());
        this.clusterSize = node.getNodes().size();
    }

    /**
     * Create Async Service.
     */
    public static Service create(final int port, @NotNull final DAO dao,
                                 @NotNull final Node node) throws IOException {
        final var acceptor = new AcceptorConfig();
        final var config = new HttpServerConfig();
        acceptor.port = port;
        config.acceptors = new AcceptorConfig[]{acceptor};
        config.maxWorkers = Runtime.getRuntime().availableProcessors();
        config.queueTime = 10;
        final Map<String, HttpClient> clusterClients = new HashMap<>();
        for (final String it : node.getNodes()) {
            if (!node.getId().equals(it) && !clusterClients.containsKey(it)) {
                clusterClients.put(it, new HttpClient(new ConnectionString(it + "?timeout=100")));
            }
        }
        return new ServiceImpl(config, dao, node, clusterClients);
    }

    @Override
    public HttpSession createSession(final Socket socket) {
        return new StorageSession(socket, this);
    }

    /**
     * Life check.
     *
     * @return Response
     */
    @Path("/v0/status")
    public Response status() {
        return Response.ok("OK");
    }

    /**
     * Method to access to DAO for single entity.
     */
    @Path("/v0/entity")
    private void entity(final Request request, final HttpSession session) throws IOException {
        final String id = request.getParameter("id=");
        if (id == null || id.isEmpty()) {
            try {
                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            } catch (IOException e) {
                logger.log(WARNING, "something has gone terribly wrong", e);
            }
            return;
        }

        boolean proxied = false;
        if (request.getHeader(PROXY_HEADER) != null) {
            proxied = true;
        }
        final String replicas = request.getParameter("replicas");
        final RF rf = RF.calculateRF(replicas, session, defaultRF, clusterSize);
        final var key = ByteBuffer.wrap(id.getBytes(StandardCharsets.UTF_8));
        final boolean proxiedF = proxied;
        if (proxied || node.getNodes().size() > 1) {
            final Coordinators clusterCoordinator = new Coordinators(node, clusterClients, dao, proxiedF);
            final String[] replicaClusters = proxied ? new String[]{node.getId()} : node.replicas(rf.getFrom(), key);
            clusterCoordinator.coordinateRequest(replicaClusters, request, rf.getAck(), session);
        } else {
            try {
                switch (request.getMethod()) {
                    case Request.METHOD_GET:
                        executeAsync(session, () -> get(key));
                        break;
                    case Request.METHOD_PUT:
                        executeAsync(session, () -> put(key, request));
                        break;
                    case Request.METHOD_DELETE:
                        executeAsync(session, () -> delete(key));
                        break;
                    default:
                        session.sendError(Response.METHOD_NOT_ALLOWED, "Wrong method");
                        break;
                }
            } catch (IOException e) {
                session.sendError(Response.INTERNAL_ERROR, e.getMessage());
            }
        }
    }

    @Override
    public void handleDefault(final Request request, final HttpSession session) throws IOException {
        switch (request.getPath()) {
            case "/v0/entity":
                entity(request, session);
                break;
            case "/v0/entities":
                entities(request, session);
                break;
            default:
                session.sendError(Response.BAD_REQUEST, "Wrong path");
                break;
        }
    }

    private void executeAsync(final HttpSession session, final Action action) {
        executor.execute(() -> {
            try {
                session.sendResponse(action.act());
            } catch (IOException e) {
                try {
                    session.sendError(Response.INTERNAL_ERROR, e.getMessage());
                } catch (IOException ex) {
                    logger.log(WARNING, e.getMessage());
                }
            }
        });
    }

    @FunctionalInterface
    interface Action {
        Response act() throws IOException;
    }

    private void entities(final Request request, final HttpSession session) throws IOException {
        final String start = request.getParameter("start=");
        if (start == null || start.isEmpty()) {
            session.sendError(Response.BAD_REQUEST, "No start");
            return;
        }

        if (request.getMethod() != Request.METHOD_GET) {
            session.sendError(Response.METHOD_NOT_ALLOWED, "Wrong method");
            return;
        }

        String end = request.getParameter("end=");
        if (end != null && end.isEmpty()) {
            end = null;
        }

        try {
            final Iterator<Record> records =
                    dao.range(ByteBuffer.wrap(start.getBytes(StandardCharsets.UTF_8)),
                            end == null ? null : ByteBuffer.wrap(end.getBytes(StandardCharsets.UTF_8)));
            ((StorageSession) session).stream(records);
        } catch (IOException e) {
            session.sendError(Response.INTERNAL_ERROR, e.getMessage());
        }
    }

    private Response put(final ByteBuffer key, final Request request) throws IOException {
        dao.upsert(key, ByteBuffer.wrap(request.getBody()));
        return new Response(Response.CREATED, Response.EMPTY);
    }

    private Response delete(final ByteBuffer key) throws IOException {
        dao.remove(key);
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    private Response get(final ByteBuffer key) {
        try {
            final ByteBuffer value = dao.get(key);
            final ByteBuffer duplicate = value.duplicate();
            final var body = new byte[duplicate.remaining()];
            duplicate.get(body);
            return new Response(Response.OK, body);
        } catch (NoSuchElementExceptionLite | IOException ex) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
    }
}
