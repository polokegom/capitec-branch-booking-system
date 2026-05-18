package za.co.capitec.booking.api.dto;

import java.util.List;

public record BranchMarketResponse(String timezone, List<String> provinces) {}
