package concert.facade;

public record PurchaseReceipt(String orderId, String seatCode, String zone, long priceCents,
                              String ticketId, String barcode, String passId, String gateId) {
}
