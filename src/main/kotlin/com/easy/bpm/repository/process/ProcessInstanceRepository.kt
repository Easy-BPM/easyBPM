package com.easy.bpm.repository.process

import com.easy.bpm.model.process.ProcessInstance
import org.springframework.data.jpa.repository.JpaRepository

interface ProcessInstanceRepository : JpaRepository<ProcessInstance, Long>