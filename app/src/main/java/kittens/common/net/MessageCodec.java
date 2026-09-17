package kittens.common.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * {@link Message} to JSON and back, wrapped in a {@code {"type":..,"data":..}} envelope so the
 * records stay plain data with no protocol bookkeeping in them.
 */
public final class MessageCodec {
  private static final Gson GSON = new Gson();

  private MessageCodec() {}

  public static String encode(Message message) {
    JsonObject envelope = new JsonObject();
    envelope.addProperty("type", typeTag(message));
    envelope.add("data", GSON.toJsonTree(message));
    return GSON.toJson(envelope);
  }

  public static Message decode(String json) {
    JsonObject envelope;
    try {
      envelope = JsonParser.parseString(json).getAsJsonObject();
    } catch (JsonSyntaxException | IllegalStateException e) {
      throw new IllegalArgumentException("not a JSON message envelope: " + json, e);
    }
    if (!envelope.has("type") || !envelope.has("data")) {
      throw new IllegalArgumentException("message envelope missing 'type' or 'data': " + json);
    }
    String type = envelope.get("type").getAsString();
    var data = envelope.get("data");
    return switch (type) {
      case "join" -> GSON.fromJson(data, Join.class);
      case "joinAccepted" -> GSON.fromJson(data, JoinAccepted.class);
      case "input" -> GSON.fromJson(data, InputCommand.class);
      case "snapshot" -> GSON.fromJson(data, Snapshot.class);
      default -> throw new IllegalArgumentException("unknown message type: " + type);
    };
  }

  private static String typeTag(Message message) {
    return switch (message) {
      case Join m -> "join";
      case JoinAccepted m -> "joinAccepted";
      case InputCommand m -> "input";
      case Snapshot m -> "snapshot";
    };
  }
}
