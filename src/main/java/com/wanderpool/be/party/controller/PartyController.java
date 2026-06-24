package com.wanderpool.be.party.controller;

import com.wanderpool.be.global.code.CommonSuccessCode;
import com.wanderpool.be.global.response.ApiResponse;
import com.wanderpool.be.party.service.PartyService;
import java.util.List;
import com.wanderpool.be.party.service.dto.CancelParticipationRequest;
import com.wanderpool.be.party.service.dto.CancelParticipationResponse;
import com.wanderpool.be.party.service.dto.PartyCreateRequest;
import com.wanderpool.be.party.service.dto.PartyCreateResponse;
import com.wanderpool.be.party.service.dto.PartyDetailResponse;
import com.wanderpool.be.party.service.dto.PartyJoinRequest;
import com.wanderpool.be.party.service.dto.PartyJoinResponse;
import com.wanderpool.be.party.service.dto.PartyParticipantSummaryResponse;
import com.wanderpool.be.party.service.dto.PartyRejectRequest;
import com.wanderpool.be.party.service.dto.UpdatePartyStatusResponse;
import com.wanderpool.be.party.service.dto.PartySearchRequest;
import com.wanderpool.be.party.service.dto.PartySummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Party", description = "파티(카풀방) API")
@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyController {

    private final PartyService partyService;

    @Operation(
            summary = "파티 검색",
            description = "승객이 입력한 출발지, 목적지, 시간, 상태 조건으로 파티 목록을 조회합니다."
    )
    @GetMapping
    public ApiResponse<List<PartySummaryResponse>> searchParties(
            @ModelAttribute PartySearchRequest request
    ) {
        List<PartySummaryResponse> response = partyService.searchParties(request);
        return ApiResponse.success(CommonSuccessCode.SUCCESS_READ_LIST, response);
    }

    @Operation(
            summary = "내가 생성한 파티 목록 조회",
            description = "인증된 멤버가 생성한 파티 목록을 조회합니다."
    )
    @GetMapping("/me/created")
    public ApiResponse<List<PartySummaryResponse>> getMyCreatedParties(
            @Parameter(description = "인증된 멤버 ID", required = true)
            @RequestHeader("X-Member-Id") Long memberId
    ) {
        List<PartySummaryResponse> response = partyService.getCreatedParties(memberId);
        return ApiResponse.success(CommonSuccessCode.SUCCESS_READ_LIST, response);
    }

    @Operation(
            summary = "내가 참여한 파티 목록 조회",
            description = "인증된 멤버가 참여 승인된 파티 목록을 조회합니다."
    )
    @GetMapping("/me/joined")
    public ApiResponse<List<PartySummaryResponse>> getMyJoinedParties(
            @Parameter(description = "인증된 멤버 ID", required = true)
            @RequestHeader("X-Member-Id") Long memberId
    ) {
        List<PartySummaryResponse> response = partyService.getJoinedParties(memberId);
        return ApiResponse.success(CommonSuccessCode.SUCCESS_READ_LIST, response);
    }

    @Operation(
            summary = "파티 상세 조회",
            description = "특정 파티 1건의 상세 정보를 조회합니다."
    )
    @GetMapping("/{partyId}")
    public ApiResponse<PartyDetailResponse> getParty(
            @Parameter(description = "파티 ID", required = true)
            @PathVariable Long partyId
    ) {
        PartyDetailResponse response = partyService.getParty(partyId);
        return ApiResponse.success(CommonSuccessCode.SUCCESS_READ, response);
    }

    @Operation(
            summary = "파티 참여자 목록 조회",
            description = "특정 파티에 참여한 참여자 목록을 조회합니다."
    )
    @GetMapping("/{partyId}/participants")
    public ApiResponse<List<PartyParticipantSummaryResponse>> getPartyParticipants(
            @Parameter(description = "파티 ID", required = true)
            @PathVariable Long partyId
    ) {
        List<PartyParticipantSummaryResponse> response = partyService.getPartyParticipants(partyId);
        return ApiResponse.success(CommonSuccessCode.SUCCESS_READ_LIST, response);
    }

    @Operation(
            summary = "파티 생성",
            description = "Driver가 출발지/목적지를 입력하여 카풀 파티(방)을 생성합니다."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PartyCreateResponse> createParty(
            @Parameter(description = "인증된 멤버 ID", required = true)
            @RequestHeader("X-Member-Id") Long memberId,

            @Valid @RequestBody PartyCreateRequest request
    ) {
        PartyCreateResponse response = partyService.createParty(memberId, request);
        return ApiResponse.created(response);
    }

    @Operation(
            summary = "파티 참여 요청",
            description = "승객이 파티에 참여를 요청합니다. 요청 후 상태는 PENDING이며, Driver가 수락/거절 API를 통해 처리합니다."
    )
    @PostMapping("/{partyId}/join")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PartyJoinResponse> joinParty(
            @Parameter(description = "파티 ID", required = true)
            @PathVariable Long partyId,

            @Parameter(description = "인증된 멤버 ID", required = true)
            @RequestHeader("X-Member-Id") Long memberId,

            @Valid @RequestBody PartyJoinRequest request
    ) {
        PartyJoinResponse response = partyService.joinParty(partyId, memberId, request);
        return ApiResponse.created(response);
    }

    @Operation(
            summary = "참여 요청 승낙",
            description = "Driver가 PENDING 상태의 참여 요청을 승낙합니다. 승낙 시 정원이 1 증가하고, 정원 도달 시 파티가 자동 CLOSED 됩니다."
    )
    @PostMapping("/{partyId}/participants/{participantId}/accept")
    public ApiResponse<PartyJoinResponse> acceptJoinRequest(
            @Parameter(description = "파티 ID", required = true)
            @PathVariable Long partyId,

            @Parameter(description = "참여 요청 ID", required = true)
            @PathVariable Long participantId,

            @Parameter(description = "인증된 멤버 ID (Driver)", required = true)
            @RequestHeader("X-Member-Id") Long memberId
    ) {
        PartyJoinResponse response = partyService.acceptJoinRequest(partyId, participantId, memberId);
        return ApiResponse.success(CommonSuccessCode.SUCCESS_UPDATE, response);
    }

    @Operation(
            summary = "참여 요청 거절",
            description = "Driver가 PENDING 상태의 참여 요청을 거절합니다. 사유(reason)는 선택입니다."
    )
    @PostMapping("/{partyId}/participants/{participantId}/reject")
    public ApiResponse<PartyJoinResponse> rejectJoinRequest(
            @Parameter(description = "파티 ID", required = true)
            @PathVariable Long partyId,

            @Parameter(description = "참여 요청 ID", required = true)
            @PathVariable Long participantId,

            @Parameter(description = "인증된 멤버 ID (Driver)", required = true)
            @RequestHeader("X-Member-Id") Long memberId,

            @Valid @RequestBody(required = false) PartyRejectRequest request
    ) {
        PartyJoinResponse response = partyService.rejectJoinRequest(partyId, participantId, memberId, request);
        return ApiResponse.success(CommonSuccessCode.SUCCESS_UPDATE, response);
    }

    @Operation(
            summary = "파티 운행 시작",
            description = "Driver가 파티를 운행 시작 상태(IN_PROGRESS)로 전환합니다."
    )
    @PostMapping("/{partyId}/in-progress")
    public ApiResponse<UpdatePartyStatusResponse> startParty(
            @RequestHeader("X-Member-Id") Long memberId,
            @PathVariable Long partyId
    ) {
        return ApiResponse.success(CommonSuccessCode.SUCCESS_UPDATE, partyService.startParty(partyId, memberId));
    }

    @Operation(
            summary = "파티 운행 종료",
            description = "Driver가 파티를 완료 상태(COMPLETED)로 전환합니다."
    )
    @PostMapping("/{partyId}/complete")
    public ApiResponse<UpdatePartyStatusResponse> completeParty(
            @RequestHeader("X-Member-Id") Long memberId,
            @PathVariable Long partyId
    ) {
        return ApiResponse.success(CommonSuccessCode.SUCCESS_UPDATE, partyService.completeParty(partyId, memberId));
    }

    @Operation(
            summary = "파티 참여 취소",
            description = "참여자가 파티 참여를 취소합니다."
    )
    @DeleteMapping("/{partyId}/participants/{participantId}")
    public ApiResponse<CancelParticipationResponse> cancelParticipation(
            @RequestHeader("X-Member-Id") Long memberId,
            @PathVariable Long partyId,
            @PathVariable Long participantId,
            @Valid @RequestBody(required = false) CancelParticipationRequest request
    ) {
        String cancelReason = request != null ? request.cancelReason() : null;
        CancelParticipationResponse response = partyService.cancelParticipation(partyId, participantId, memberId, cancelReason);
        return ApiResponse.success(CommonSuccessCode.SUCCESS_DELETE, response);
    }
}
