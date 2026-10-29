package com.courseplanner.api.api.services;

import com.courseplanner.api.api.dto.BulletinListResponse;

public interface BulletinQueryService {
    BulletinListResponse listRecent(int limit);
}
