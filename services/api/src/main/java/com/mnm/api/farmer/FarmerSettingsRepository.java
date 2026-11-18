package com.mnm.api.farmer;

import com.mnm.api.domain.user.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmerSettingsRepository extends JpaRepository<UserSettings, Long> {
}
