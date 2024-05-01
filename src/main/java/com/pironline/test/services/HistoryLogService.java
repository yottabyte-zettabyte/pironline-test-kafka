package com.pironline.test.services;

import com.pironline.test.enums.OperationType;
import com.pironline.test.models.cdc.CdcEvent;
import com.pironline.test.models.pojo.Identifiable;
import com.pironline.test.persistences.HistoryLog;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryLogService {

    private final Clock clock;
    private final HistoryLogServiceTxn historyLogServiceTxn;

    public void save(CdcEvent cdcEvent, String tableName) {
        if (cdcEvent == null || cdcEvent.getPayload() == null || StringUtils.isBlank(tableName)) {
            return;
        }

        log.debug("Operation Type - [{}]", cdcEvent.getPayload().getOp());
        log.debug("Before - [{}]", cdcEvent.getPayload().getBefore());
        log.debug("After  - [{}]", cdcEvent.getPayload().getAfter());

        try {
            if (OperationType.UPDATE.getOp().equals(cdcEvent.getPayload().getOp())) {
                Identifiable before = cdcEvent.getPayload().getBefore();
                Identifiable after = cdcEvent.getPayload().getAfter();
                List<HistoryLog> historyLogs = getUpdateHistory(before, after, tableName, before.getId().toString());
                saveHistoryLogs(historyLogs);
            }
            else if (OperationType.INSERT.getOp().equals(cdcEvent.getPayload().getOp())) {
                Identifiable after = cdcEvent.getPayload().getAfter();
                List<HistoryLog> historyLogs = getNonUpdateHistory(OperationType.INSERT, after, tableName, after.getId().toString());
                saveHistoryLogs(historyLogs);
            }
            else if (OperationType.DELETE.getOp().equals(cdcEvent.getPayload().getOp())) {
                Identifiable before = cdcEvent.getPayload().getBefore();
                List<HistoryLog> historyLogs = getNonUpdateHistory(OperationType.DELETE, before, tableName, before.getId().toString());
                saveHistoryLogs(historyLogs);
            }
        }
        catch (final Exception ex) {
            log.error("Error while saving company logs: ", ex);
        }
    }

    private List<HistoryLog> getUpdateHistory(Object before, Object after, String tableName, String objectId) throws IllegalAccessException {
        List<HistoryLog> historyLogs = new ArrayList<>();

        for (Field beforeField : before.getClass().getDeclaredFields()) {
            for (Field afterField : after.getClass().getDeclaredFields()) {
                if (beforeField.getName().equals(afterField.getName())) {

                    beforeField.setAccessible(true);
                    afterField.setAccessible(true);
                    Object oldValue = beforeField.get(before);
                    Object newValue = afterField.get(after);

                    if (!Objects.equals(oldValue, newValue)) {
                        HistoryLog historyLog = HistoryLog
                                .builder()
                                .logType(OperationType.UPDATE.getLogType())
                                .tableId(tableName)
                                .fieldId(beforeField.getName())
                                .objectId(objectId)
                                .newValue(Objects.nonNull(newValue) ? newValue.toString() : null)
                                .oldValue(Objects.nonNull(oldValue) ? oldValue.toString() : null)
                                .createdAt(LocalDateTime.now(clock))
                                .build();
                        historyLogs.add(historyLog);
                    }
                    break;
                }
            }
        }
        return historyLogs;
    }

    private List<HistoryLog> getNonUpdateHistory(OperationType operationType, Object payload, String tableName, String objectId) throws IllegalAccessException {
        List<HistoryLog> historyLogs = new ArrayList<>();

        for (Field field : payload.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            Object newValue = (operationType == OperationType.INSERT) ? field.get(payload) : null;
            Object oldValue = (operationType == OperationType.DELETE) ? field.get(payload) : null;

            HistoryLog historyLog = HistoryLog
                    .builder()
                    .logType(operationType.getLogType())
                    .tableId(tableName)
                    .fieldId(field.getName())
                    .objectId(objectId)
                    .newValue(Objects.nonNull(newValue) ? newValue.toString() : null)
                    .oldValue(Objects.nonNull(oldValue) ? oldValue.toString() : null)
                    .createdAt(LocalDateTime.now(clock))
                    .build();
            historyLogs.add(historyLog);
        }
        return historyLogs;
    }

    private void saveHistoryLogs(List<HistoryLog> historyLogs) {
        if (!CollectionUtils.isEmpty(historyLogs)) {
            historyLogServiceTxn.save(historyLogs);
        }
    }
}
