package io.github.ajax1.campusradar.profile;

import io.github.ajax1.campusradar.job.RecruitType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Converter
public class RecruitTypeListConverter implements AttributeConverter<List<RecruitType>, String> {

    @Override
    public String convertToDatabaseColumn(List<RecruitType> list) {
        return list == null || list.isEmpty() ? null : list.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public List<RecruitType> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(RecruitType::parse).filter(Objects::nonNull).toList();
    }
}
