package concert.facade;

public record PurchaseRequest(String buyerId, String email, String seatCode) {
}
