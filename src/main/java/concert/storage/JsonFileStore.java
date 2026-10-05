package concert.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class JsonFileStore<T> {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final TypeToken<List<T>> listType;

    @SuppressWarnings("unchecked")
    public JsonFileStore(Path file, Class<T> elementType) {
        this.file = file;
        this.listType = (TypeToken<List<T>>) TypeToken.getParameterized(List.class, elementType);
    }

    public Path file() {
        return file;
    }

    public synchronized List<T> readAll() {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            List<T> items = GSON.fromJson(json, listType.getType());
            return items == null ? new ArrayList<>() : new ArrayList<>(items);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    public synchronized void upsert(T item, Function<T, String> idOf) {
        List<T> all = readAll();
        String id = idOf.apply(item);
        for (int i = 0; i < all.size(); i++) {
            if (idOf.apply(all.get(i)).equals(id)) {
                all.set(i, item);
                writeAll(all);
                return;
            }
        }
        all.add(item);
        writeAll(all);
    }

    public synchronized void writeAll(List<T> items) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(items, listType.getType()), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }
}
