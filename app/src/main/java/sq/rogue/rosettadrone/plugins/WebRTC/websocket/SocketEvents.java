package sq.rogue.rosettadrone.plugins.WebRTC.websocket;

import okhttp3.Response;

final class SocketEvents {

    final public static class MessageEvent {
        String message;

        MessageEvent(String message) {
            this.message = message;
        }
    }

    final public static class BaseMessageEvent {
        String name;

        BaseMessageEvent(String name) {
            this.name = name;
        }
    }

    final public static class ResponseMessageEvent {
        String name;
        String data;

        ResponseMessageEvent(String name, String data) {
            this.name = name;
            this.data = data;
        }
    }

    final public static class OpenStatusEvent {
        Response response;

        OpenStatusEvent(Response response) {
            this.response = response;
        }
    }

    final public static class CloseStatusEvent {
        int code;
        String reason;

        CloseStatusEvent(int code, String reason) {
            this.code = code;
            this.reason = reason;
        }
    }

    final public static class FailureStatusEvent {
        Throwable throwable;

        FailureStatusEvent(Throwable throwable) {
            this.throwable = throwable;
        }
    }

    final public static class ReconnectStatusEvent {
        int attemptsCount;
        long attemptDelay;

        ReconnectStatusEvent(int attemptsCount, long attemptDelay) {
            this.attemptsCount = attemptsCount;
            this.attemptDelay = attemptDelay;
        }
    }

    final public static class ChangeStatusEvent {
        SocketState status;

        ChangeStatusEvent(SocketState status) {
            this.status = status;
        }
    }
}
