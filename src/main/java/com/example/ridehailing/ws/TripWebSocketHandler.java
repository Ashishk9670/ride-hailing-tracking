package com.example.ridehailing.ws;

import com.example.ridehailing.service.TripEventBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/** Raw WebSocket: ws://host/ws/trips/{tripId} pushes {"kind":"location"|"event","data":{...}} JSON frames. */
@Component
public class TripWebSocketHandler extends TextWebSocketHandler {
    private static final String SUB = "subscription";
    private final TripEventBroadcaster broadcaster;
    private final ObjectMapper mapper;

    public TripWebSocketHandler(TripEventBroadcaster broadcaster, ObjectMapper mapper) {
        this.broadcaster = broadcaster;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String path = session.getUri().getPath();
        String tripId = path.substring(path.lastIndexOf('/') + 1);
        Flux<Frame> frames = Flux.merge(
                broadcaster.locations(tripId).map(l -> new Frame("location", l)),
                broadcaster.events(tripId).map(e -> new Frame("event", e)));
        Disposable d = frames.subscribe(f -> send(session, f));
        session.getAttributes().put(SUB, d);
    }

    private void send(WebSocketSession session, Frame f) {
        try {
            String json = mapper.writeValueAsString(f);
            synchronized (session) {
                if (session.isOpen()) session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            // client gone; cleaned up in afterConnectionClosed
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object d = session.getAttributes().remove(SUB);
        if (d instanceof Disposable disp) disp.dispose();
    }

    public record Frame(String kind, Object data) {}
}
