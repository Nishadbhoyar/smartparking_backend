package com.smartparking.controller;

import com.smartparking.service.PromoService;
import com.smartparking.dtos.request.ApplyPromoRequestDTO;
import com.smartparking.dtos.request.CreatePromoRequestDTO;
import com.smartparking.dtos.response.PromoResponseDTO;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/promo")
public class PromoController {

    private final PromoService promoService;
    public PromoController(PromoService promoService) { this.promoService = promoService; }

    @PostMapping("/create")
    public ResponseEntity<PromoResponseDTO> createPromo(@RequestBody CreatePromoRequestDTO dto) {
        return new ResponseEntity<>(promoService.createPromo(dto), HttpStatus.CREATED);
    }

    @GetMapping("/validate")
    public ResponseEntity<PromoResponseDTO> validate(
            @RequestParam String code,
            @RequestParam Double amount,
            @RequestParam(required = false) Long customerId) {
        return ResponseEntity.ok(promoService.validatePromo(code, amount, customerId));
    }

    @PostMapping("/apply")
    public ResponseEntity<PromoResponseDTO> applyPromo(@RequestBody ApplyPromoRequestDTO dto) {
        return ResponseEntity.ok(promoService.applyPromo(dto));
    }

    @GetMapping("/all")
    public ResponseEntity<List<PromoResponseDTO>> getAllPromos() {
        return ResponseEntity.ok(promoService.getAllPromos());
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<PromoResponseDTO> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(promoService.deactivatePromo(id));
    }

    /**
     * GET /api/promo/eligible?customerId=42
     * GET /api/promo/eligible?customerId=42&amount=300
     *
     * Returns only promos the customer is eligible to use.
     * Used by the "Available Promos" panel on the booking page.
     */
    @GetMapping("/eligible")
    public ResponseEntity<List<PromoResponseDTO>> getEligiblePromos(
            @RequestParam Long customerId,
            @RequestParam(required = false) Double amount) {
        return ResponseEntity.ok(promoService.getEligiblePromos(customerId, amount));
    }
}