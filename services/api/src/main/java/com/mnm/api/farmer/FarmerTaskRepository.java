package com.mnm.api.farmer;

import com.mnm.api.domain.task.Task;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmerTaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByFarmIdAndDoneAtIsNullOrderByDueDateAscIdAsc(Long farmId);

    List<Task> findByFarmIdAndKindInAndRefIdAndDoneAtIsNull(Long farmId, List<String> kinds, Long refId);
}
