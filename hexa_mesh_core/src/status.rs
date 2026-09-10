use std::sync::Mutex;

// Values must stay in sync with EngineStatus.kt (Kotlin bridge).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(i32)]
pub enum Status {
    Stopped = 0,
    Starting = 1,
    Running = 2,
    Error = 3,
}

#[derive(Clone, Debug)]
pub struct Snapshot {
    pub state: i32,
    pub message: String,
}

static SNAPSHOT: Mutex<Snapshot> = Mutex::new(Snapshot {
    state: Status::Stopped as i32,
    message: String::new(),
});

pub fn set(status: Status, message: impl Into<String>) {
    let mut snap = SNAPSHOT.lock().unwrap_or_else(|e| e.into_inner());
    snap.state = status as i32;
    snap.message = message.into();
}

pub fn transition(from: Status, to: Status, message: impl Into<String>) -> bool {
    let mut snap = SNAPSHOT.lock().unwrap_or_else(|e| e.into_inner());
    if snap.state == from as i32 {
        snap.state = to as i32;
        snap.message = message.into();
        true
    } else {
        false
    }
}

pub fn snapshot() -> Snapshot {
    SNAPSHOT.lock().unwrap_or_else(|e| e.into_inner()).clone()
}

pub fn current() -> i32 {
    SNAPSHOT.lock().unwrap_or_else(|e| e.into_inner()).state
}
