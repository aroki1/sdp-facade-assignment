package concert.facade;

@FunctionalInterface
public interface ChainCheckpointListener {
    ChainCheckpointListener NONE = order -> { };

    void checkpointed(OrderRecord order);
}
