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
            let val = env.get_field(obj, JNIString::new(name), jni_sig!("Ljava/lang/String;"))?;
            let jstr = JString::cast_local(env, val.l()?)?;
            jstr.try_to_string(env)
        };

        Ok(Self {
            model_path: get_str("modelPath")?,
            lib_dir: get_str("nativeLibDir")?,
            cache_dir: get_str("cacheDir")?,
            backend: get_str("backend")?,
            port: env
                .get_field(obj, JNIString::new("port"), jni_sig!("I"))?
                .i()?,
        })
    }
}
