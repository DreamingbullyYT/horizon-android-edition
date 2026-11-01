//! Mojang-verified resource installation for the Android launcher.

pub use horizon_core;
pub type Result<T> = std::result::Result<T, Box<dyn std::error::Error>>;

#[path = "../../horizon-client/src/android_resources.rs"]
pub mod android_resources;
#[path = "../../horizon-client/src/official_install.rs"]
pub mod official_install;
#[path = "../../horizon-client/src/resource_install.rs"]
pub mod resource_install;
