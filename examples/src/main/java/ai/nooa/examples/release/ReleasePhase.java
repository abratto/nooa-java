package ai.nooa.examples.release;

/**
 * A payload-free release pipeline encoded as an enum with an abstract
 * transition method. This is the compact alternative to the sealed-types
 * state machine in {@link ai.nooa.examples.incident.IncidentStateMachine}:
 * ideal when states carry no data and the FSM is small enough to read in one
 * file.
 *
 * <p>Compare with the sealed approach: the enum keeps everything local and has
 * zero boilerplate, but cannot carry per-state payloads ({@code RolledBack}
 * here has no reason, {@code Deployed} no artifact id). Use the enum when the
 * states are genuinely payload-free.</p>
 */
public enum ReleasePhase {

    PLANNED {
        @Override
        public ReleasePhase advance(ReleaseEvent event) {
            return switch (event) {
                case START_BUILD -> BUILDING;
                default -> illegal(this, event);
            };
        }
    },
    BUILDING {
        @Override
        public ReleasePhase advance(ReleaseEvent event) {
            return switch (event) {
                case TESTS_PASS -> TESTING;
                case TESTS_FAIL -> REJECTED;
                default -> illegal(this, event);
            };
        }
    },
    TESTING {
        @Override
        public ReleasePhase advance(ReleaseEvent event) {
            return switch (event) {
                case APPROVE -> APPROVED;
                case TESTS_FAIL, REJECT -> REJECTED;
                default -> illegal(this, event);
            };
        }
    },
    APPROVED {
        @Override
        public ReleasePhase advance(ReleaseEvent event) {
            return switch (event) {
                case DEPLOY -> DEPLOYED;
                default -> illegal(this, event);
            };
        }
    },
    DEPLOYED {
        @Override
        public ReleasePhase advance(ReleaseEvent event) {
            return switch (event) {
                case ROLLBACK -> ROLLED_BACK;
                default -> illegal(this, event);
            };
        }
    },
    REJECTED {
        @Override
        public ReleasePhase advance(ReleaseEvent event) {
            return illegal(this, event);
        }
    },
    ROLLED_BACK {
        @Override
        public ReleasePhase advance(ReleaseEvent event) {
            return illegal(this, event);
        }
    };

    /** Apply an event; illegal transitions raise {@link IllegalReleaseTransition}. */
    public abstract ReleasePhase advance(ReleaseEvent event);

    public boolean terminal() {
        return this == DEPLOYED || this == REJECTED || this == ROLLED_BACK;
    }

    private static ReleasePhase illegal(ReleasePhase phase, ReleaseEvent event) {
        throw new IllegalReleaseTransition(
            "Cannot apply " + event + " while in phase " + phase);
    }
}
