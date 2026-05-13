package com.smartparking.service;

import com.smartparking.dtos.request.FeedbackRequestDTO;
import com.smartparking.dtos.response.FeedbackResponseDTO;

import java.util.List;

public interface FeedbackService {
    FeedbackResponseDTO submitFeedback(FeedbackRequestDTO requestDTO);
    List<FeedbackResponseDTO> getLotFeedback(Long lotId);
    List<FeedbackResponseDTO> getValetFeedback(Long valetId);
    Double getLotAverageRating(Long lotId);
    Double getValetAverageRating(Long valetId);
    void deleteFeedback(Long feedbackId);
}