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
}

impl ServerConfig {
    pub fn from_jobject<'local>(
        env: &mut jni::Env<'local>,
        obj: &JObject<'local>,
    ) -> jni::errors::Result<Self> {
        let mut get_str = |name: &str| -> jni::errors::Result<String> {
            let jobj = env
                .get_field(obj, JNIString::new(name), jni_sig!("Ljava/lang/String;"))?
                .l()?;
            let jstr = JString::cast_local(env, jobj)?;
            jstr.try_to_string(env)
        };

        let model_path = get_str("modelPath")?;
        let lib_dir = get_str("nativeLibDir")?;
        let cache_dir = get_str("cacheDir")?;
        let backend = get_str("backend")?;

        let port = env
            .get_field(obj, JNIString::new("port"), jni_sig!("I"))?
            .i()?;

        Ok(Self {
            model_path,
            lib_dir,
            cache_dir,
            port,
            backend,
        })
    }
}
