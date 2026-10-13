package com.git.hui.jobclaw.core.apis.permission;

import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;

import java.util.Objects;

/**
 *
 * @date 2026/4/21
 */
public enum AgentPermission {
    TOTAL {
        @Override
        public boolean enabled(UserRoleEnum role) {
            return true;
        }
    },

    VIP {
        @Override
        public boolean enabled(UserRoleEnum role) {
            return Objects.equals(role, UserRoleEnum.VIP);
        }
    },
    ADMIN {
        @Override
        public boolean enabled(UserRoleEnum role) {
            return Objects.equals(role, UserRoleEnum.ADMIN);
        }
    },
    ;

    public abstract boolean enabled(UserRoleEnum role);
}
