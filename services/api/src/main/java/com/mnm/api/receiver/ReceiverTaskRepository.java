package com.mnm.api.receiver;

import com.mnm.api.domain.task.Task;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReceiverTaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByFarmIdInAndDoneAtIsNullOrderByDueDateAscIdAsc(Collection<Long> farmIds);
}
