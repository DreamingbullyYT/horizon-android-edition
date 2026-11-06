//! Small command-line front end used by the Android Termux compiler.
use horizon_hnmod_core::{is_revision, read_bundle, resolve_compatibility, EngineCandidate};
use serde_json::json;
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Component, Path};

fn main() {
    if let Err(error) = run() {
        eprintln!("hnmod: {error}");
        std::process::exit(2);
    }
}

fn run() -> Result<(), Box<dyn std::error::Error>> {
    let mut args = std::env::args().skip(1);
    match args.next().as_deref() {
        Some("inspect") => {
            let archive = args.next().ok_or("usage: hnmod inspect FILE.hnmod")?;
            if args.next().is_some() { return Err("too many arguments".into()); }
            let bundle = read_bundle(std::fs::File::open(archive)?)?;
            println!("{}", serde_json::to_string(&json!({
                "manifest": bundle.manifest,
                "sha256": bundle.sha256
            }))?);
        }
        Some("extract") => {
            let archive = args.next().ok_or("usage: hnmod extract FILE.hnmod NEW_DIRECTORY")?;
            let destination = args.next().ok_or("usage: hnmod extract FILE.hnmod NEW_DIRECTORY")?;
            if args.next().is_some() { return Err("too many arguments".into()); }
            let bundle = read_bundle(std::fs::File::open(archive)?)?;
            let root = Path::new(&destination);
            fs::create_dir(root)?;
            let prefix = format!("mixins/{}/", bundle.manifest.id);
            let mut count = 0usize;
            for (name, bytes) in &bundle.files {
                let Some(relative) = name.strip_prefix(&prefix) else { continue };
                let relative_path = Path::new(relative);
                if relative_path.as_os_str().is_empty()
                    || !relative_path.components().all(|part| matches!(part, Component::Normal(_)))
                {
                    return Err(format!("unsafe relative path: {relative}").into());
                }
                let target = root.join(relative_path);
                if let Some(parent) = target.parent() { fs::create_dir_all(parent)?; }
                let mut output = OpenOptions::new().write(true).create_new(true).open(&target)?;
                output.write_all(bytes)?;
                count += 1;
            }
            if count == 0 { return Err("bundle contains no mixin files".into()); }
            println!("{}", serde_json::to_string(&json!({
                "id": bundle.manifest.id,
                "version": bundle.manifest.version,
                "files": count,
                "sha256": bundle.sha256
            }))?);
        }
        Some("resolve") => {
            let revision = args.next().ok_or("usage: hnmod resolve REVISION HOOKS_API MIXINS_REV MOD...")?;
            let hooks_api: u32 = args.next().ok_or("missing hooks API")?.parse()?;
            let mixins_revision = args.next().ok_or("missing mixins revision")?;
            if !is_revision(&revision) || !is_revision(&mixins_revision) {
                return Err("engine and mixins revisions must be full lowercase 40-character commit IDs".into());
            }
            let paths: Vec<_> = args.collect();
            if paths.is_empty() { return Err("give at least one .hnmod archive".into()); }
            let bundles = paths.iter()
                .map(|path| read_bundle(std::fs::File::open(path)?))
                .collect::<Result<Vec<_>, Box<dyn std::error::Error>>>()?;
            let candidate = EngineCandidate { revision, hooks_api, mixins_revision };
            let plan = resolve_compatibility(&bundles, &[candidate])?;
            println!("{}", serde_json::to_string(&json!({
                "revision": plan.selected.revision,
                "hooks_api": plan.selected.hooks_api,
                "mixins_revision": plan.selected.mixins_revision,
                "mods": plan.mods,
                "warnings": plan.warnings
            }))?);
        }
        _ => {
            eprintln!("Usage:");
            eprintln!("  hnmod inspect FILE.hnmod");
            eprintln!("  hnmod extract FILE.hnmod NEW_DIRECTORY");
            eprintln!("  hnmod resolve REVISION HOOKS_API MIXINS_REV MOD.hnmod...");
            return Err("unknown command".into());
        }
    }
    Ok(())
}
