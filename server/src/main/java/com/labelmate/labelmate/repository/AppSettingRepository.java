package com.labelmate.labelmate.repository;

import com.labelmate.labelmate.model.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
