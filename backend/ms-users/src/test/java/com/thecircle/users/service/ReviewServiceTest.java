package com.thecircle.users.service;

import com.thecircle.users.dto.ReviewDto;
import com.thecircle.users.model.Review;
import com.thecircle.users.model.User;
import com.thecircle.users.repository.ReviewRepository;
import com.thecircle.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock ReviewRepository reviewRepository;
    @Mock UserRepository userRepository;
    @Mock ContractsClient contractsClient;
    @InjectMocks ReviewService reviewService;

    private ReviewDto input(String contractId, Double rating) {
        return new ReviewDto(null, null, null, null, contractId, rating, "Good", null);
    }

    private ContractsClient.ContractSummary summary(boolean reviewable) {
        return new ContractsClient.ContractSummary("c1", "1", "2", reviewable);
    }

    @Test
    void selfReview_rejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reviewService.create(1L, 1L, input("c1", 5.0)));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void nonHalfStepRating_rejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reviewService.create(1L, 2L, input("c1", 4.3)));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void unverifiableContract_rejected() {
        when(contractsClient.getContract("c1")).thenReturn(null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reviewService.create(1L, 2L, input("c1", 5.0)));
        assertEquals(422, ex.getStatusCode().value());
    }

    @Test
    void notYetReviewable_rejected() {
        when(contractsClient.getContract("c1")).thenReturn(summary(false));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reviewService.create(1L, 2L, input("c1", 5.0)));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void reviewerNotAParty_rejected() {
        when(contractsClient.getContract("c1")).thenReturn(summary(true));
        // contract is between users 1 and 2; user 9 reviewing 2 is not a party
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reviewService.create(9L, 2L, input("c1", 5.0)));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void duplicateReview_rejected() {
        when(contractsClient.getContract("c1")).thenReturn(summary(true));
        when(reviewRepository.existsByReviewerIdAndContractId(1L, "c1")).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reviewService.create(1L, 2L, input("c1", 5.0)));
        assertEquals(409, ex.getStatusCode().value());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void validReview_persisted() {
        when(contractsClient.getContract("c1")).thenReturn(summary(true));
        when(reviewRepository.existsByReviewerIdAndContractId(1L, "c1")).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(
                User.builder().id(2L).firstName("Tar").lastName("Get").build()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                User.builder().id(1L).firstName("Re").lastName("Viewer").build()));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewDto out = reviewService.create(1L, 2L, input("c1", 4.5));

        assertEquals(4.5, out.rating());
        assertEquals(2L, out.targetUserId());
        assertEquals("Re Viewer", out.reviewerName());
        verify(reviewRepository).save(any(Review.class));
    }
}
