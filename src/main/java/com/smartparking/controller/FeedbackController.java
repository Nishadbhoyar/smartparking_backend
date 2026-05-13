package com.smartparking.controller;

import com.smartparking.dtos.request.FeedbackRequestDTO;
import com.smartparking.dtos.response.FeedbackResponseDTO;
import com.smartparking.service.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    // POST: Submit a new review
    @PostMapping("/submit")
    public ResponseEntity<FeedbackResponseDTO> submitFeedback(@RequestBody FeedbackRequestDTO requestDTO) {
        FeedbackResponseDTO savedFeedback = feedbackService.submitFeedback(requestDTO);
        return new ResponseEntity<>(savedFeedback, HttpStatus.CREATED);
    }

    // GET: All reviews for a specific parking lot
    @GetMapping("/lot/{lotId}")
    public ResponseEntity<List<FeedbackResponseDTO>> getLotFeedback(@PathVariable Long lotId) {
        return new ResponseEntity<>(feedbackService.getLotFeedback(lotId), HttpStatus.OK);
    }

    // GET: The average star rating for a parking lot
    @GetMapping("/lot/{lotId}/average")
    public ResponseEntity<Double> getLotAverageRating(@PathVariable Long lotId) {
        return new ResponseEntity<>(feedbackService.getLotAverageRating(lotId), HttpStatus.OK);
    }

    // GET: All reviews for a specific valet
    @GetMapping("/valet/{valetId}")
    public ResponseEntity<List<FeedbackResponseDTO>> getValetFeedback(@PathVariable Long valetId) {
        return new ResponseEntity<>(feedbackService.getValetFeedback(valetId), HttpStatus.OK);
    }

    // GET: The average star rating for a valet
    @GetMapping("/valet/{valetId}/average")
    public ResponseEntity<Double> getValetAverageRating(@PathVariable Long valetId) {
        return new ResponseEntity<>(feedbackService.getValetAverageRating(valetId), HttpStatus.OK);
    }

    // DELETE: Delete a review
    @DeleteMapping("/{feedbackId}")
    public ResponseEntity<String> deleteFeedback(@PathVariable Long feedbackId) {
        feedbackService.deleteFeedback(feedbackId);
        return ResponseEntity.ok("Review deleted successfully");
    }
}