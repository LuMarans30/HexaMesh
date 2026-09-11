use jni::{
    jni_sig,
    objects::{JObject, JString},
    strings::JNIString,
};

#[derive(Debug, Clone)]
pub struct ServerConfig {
    pub model_path: String,
    pub lib_dir: String,
    pub cache_dir: String,
    pub port: i32,
    pub backend: String,
    pub api_key: String,
    pub extra_args: Vec<String>,
    /// "server" (OpenAI API) or "rpc" (expose this device to a coordinator).
    pub role: String,
    /// Comma-joined `host:port` peers passed to llama-server's `--rpc`.
    pub rpc_servers: String,
}

impl ServerConfig {
    pub fn from_jobject<'local>(
        env: &mut jni::Env<'local>,
        obj: &JObject<'local>,
    ) -> jni::errors::Result<Self> {
        let mut get_str = |name: &str| -> jni::errors::Result<String> {
            let val = env.get_field(obj, JNIString::new(name), jni_sig!("Ljava/lang/String;"))?;
            let jstr = JString::cast_local(env, val.l()?)?;
            jstr.try_to_string(env)
        };

        let extra_args = get_str("extraArgs")?
            .split('\n')
            .map(str::trim)
            .filter(|arg| !arg.is_empty())
            .map(String::from)
            .collect();

        // Read role through the closure before the raw field access below, so the
        // closure's mutable borrow of `env` has ended.
        let role = get_str("role")?;
        let rpc_servers = get_str("rpcServers")?;

        Ok(Self {
            model_path: get_str("modelPath")?,
            lib_dir: get_str("nativeLibDir")?,
            cache_dir: get_str("cacheDir")?,
            backend: get_str("backend")?,
            api_key: get_str("apiKey")?,
            port: env
                .get_field(obj, JNIString::new("port"), jni_sig!("I"))?
                .i()?,
            extra_args,
            role,
            rpc_servers,
        })
    }

    pub fn is_rpc(&self) -> bool {
        self.role.eq_ignore_ascii_case("rpc")
    }
}
