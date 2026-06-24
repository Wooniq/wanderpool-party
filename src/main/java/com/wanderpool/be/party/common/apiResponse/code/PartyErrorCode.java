package com.wanderpool.be.party.common.apiResponse.code;

import com.wanderpool.be.global.code.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PartyErrorCode implements ErrorCode {
    PARTY_NOT_FOUND(HttpStatus.NOT_FOUND, "PARTY_404", "파티를 찾을 수 없습니다."),

    NOT_DRIVER(HttpStatus.FORBIDDEN, "PARTY_403_1", "Driver만 파티를 생성할 수 있습니다."),
    WAYPOINT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "PARTY_400_1", "경유지는 최대 3곳입니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "PARTY_400_2", "허용되지 않는 상태 변경입니다."),

    ALREADY_JOINED(HttpStatus.CONFLICT, "PARTICIPANT_409_1", "이미 참여한 파티입니다."),
    CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "PARTICIPANT_409_2", "정원이 초과되었습니다."),
    PARTY_NOT_RECRUITING(HttpStatus.CONFLICT, "PARTICIPANT_409_3", "모집 중인 파티가 아닙니다."),
    ALREADY_CANCELLED(HttpStatus.CONFLICT, "PARTICIPANT_409_4", "이미 취소된 참여입니다."),
    CANNOT_CANCEL(HttpStatus.CONFLICT, "PARTICIPANT_409_5", "진행 중이거나 완료된 파티는 취소할 수 없습니다."),
    NOT_PARTICIPANT(HttpStatus.NOT_FOUND, "PARTICIPANT_404_1", "참여 정보를 찾을 수 없습니다."),
    CANNOT_JOIN_OWN_PARTY(HttpStatus.BAD_REQUEST, "PARTICIPANT_400_1", "본인 파티에 참여할 수 없습니다."),
    PARTICIPANT_NOT_PENDING(HttpStatus.CONFLICT, "PARTICIPANT_409_6", "대기 중인 참여 요청이 아닙니다."),
    NOT_PARTY_DRIVER(HttpStatus.FORBIDDEN, "PARTY_403_2", "해당 파티의 Driver만 처리할 수 있습니다."),

    INSUFFICIENT_POINTS(HttpStatus.BAD_REQUEST, "POINT_400_1", "포인트가 부족합니다."),
    MEMBER_SERVICE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "EXTERNAL_503_1", "멤버 서비스 호출에 실패했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_401_1", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_403_1", "권한이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    PartyErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
