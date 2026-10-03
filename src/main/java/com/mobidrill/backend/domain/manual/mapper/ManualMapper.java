package com.mobidrill.backend.domain.manual.mapper;

import com.mobidrill.backend.domain.manual.dto.ManualFileResDto;
import com.mobidrill.backend.domain.manual.dto.ManualRegisterReqDto;
import com.mobidrill.backend.domain.manual.dto.ManualRegisterResDto;
import com.mobidrill.backend.domain.manual.entity.Manual;
import com.mobidrill.backend.domain.manual.entity.ManualFile;
import com.mobidrill.backend.domain.manual.service.module.StoredManualFile;
import com.mobidrill.backend.domain.training.entity.TrainingField;
import com.mobidrill.backend.domain.user.entity.User;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ManualMapper {

    /**
     * 등록 정보를 교범 엔티티로 변환한다.
     * @param request : 교범 정보
     * @param field : 훈련 분야
     * @param user : 등록자
     * @return : 저장할 교범
     */
    public Manual toManual(ManualRegisterReqDto request, TrainingField field, User user) {
        return Manual.builder().title(request.manualTitle().strip()).description(request.manualDescription())
                .trainingField(field).registeredBy(user).build();
    }

    /**
     * 저장 파일 메타데이터를 교범 파일 엔티티로 변환한다.
     * @param manual : 교범
     * @param stored : 저장한 파일
     * @return : 저장할 파일 메타데이터
     */
    public ManualFile toManualFile(Manual manual, StoredManualFile stored) {
        return ManualFile.builder().manual(manual).originalName(stored.originalName())
                .fileType(stored.fileType()).storageKey(stored.storageKey()).sizeBytes(stored.sizeBytes()).build();
    }

    /**
     * 교범과 파일을 등록 응답으로 변환한다.
     * @param manual : 등록 교범
     * @param file : 등록 파일
     * @return : 교범 및 파일 메타데이터 응답
     */
    public ManualRegisterResDto toRegisterResDto(Manual manual, ManualFile file) {
        String extension = file.getOriginalName().substring(file.getOriginalName().lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
        ManualFileResDto fileDto = ManualFileResDto.builder().manualFileId(file.getId())
                .originalName(file.getOriginalName()).fileType(file.getFileType()).storageKey(file.getStorageKey())
                .storedFileName(file.getStorageKey() + "." + extension).sizeBytes(file.getSizeBytes()).build();
        return ManualRegisterResDto.builder().manualId(manual.getId()).manualTitle(manual.getTitle())
                .trainingFieldId(manual.getTrainingField() == null ? null : manual.getTrainingField().getId())
                .manualDescription(manual.getDescription())
                .manualStatus(manual.getStatus()).file(fileDto).build();
    }
}
