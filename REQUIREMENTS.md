# Distributed Disk Register - Sistem Gereksinimleri

## 📋 Gereksinimler

### Zorunlu Gereksinimler

#### 1. Java Development Kit (JDK)
- **Versiyon:** Java 17 veya üzeri
- **İndirme:** [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) veya [OpenJDK](https://openjdk.org/)
- **Kurulum Kontrolü:**
  ```bash
  java -version
  javac -version
  ```

#### 2. Apache Maven
- **Versiyon:** Maven 3.6.0 veya üzeri
- **İndirme:** [Maven Download](https://maven.apache.org/download.cgi)
- **Kurulum Kontrolü:**
  ```bash
  mvn -version
  ```

### Opsiyonel Araçlar

#### 3. IDE (Önerilen)
- **IntelliJ IDEA** (Ultimate veya Community Edition)
  - Öğrenci lisansı: [JetBrains Student License](https://www.jetbrains.com/student/)
- **Eclipse IDE for Java Developers**
- **Visual Studio Code** + Java Extension Pack

#### 4. Network Test Araçları
- **netcat (nc)** - TCP mesaj testi için
  - Windows: [Nmap ncat](https://nmap.org/download.html)
  - Linux/Mac: Genellikle yüklü gelir
- **telnet** - Alternatif TCP test aracı
  - Windows: `dism /online /Enable-Feature /FeatureName:TelnetClient`

## 📦 Bağımlılıklar

Proje aşağıdaki Maven bağımlılıklarını kullanmaktadır:

### gRPC ve Protobuf
- **gRPC Netty Shaded:** v1.67.1
- **gRPC Stub:** v1.67.1
- **gRPC Protobuf:** v1.67.1
- **Protobuf Java:** v3.25.3

### Diğer Bağımlılıklar
- **javax.annotation-api:** v1.3.2

### Build Plugins
- **protobuf-maven-plugin:** v0.6.1
- **os-maven-plugin:** v1.7.0
- **exec-maven-plugin:** v3.1.0

## 🔧 Kurulum Adımları

### 1. Java Kurulumu

#### Windows:
1. [Oracle JDK 17](https://www.oracle.com/java/technologies/downloads/#java17) indir
2. Kurulum yap
3. Sistem ortam değişkenlerine `JAVA_HOME` ekle
4. `PATH` değişkenine `%JAVA_HOME%\bin` ekle

#### Linux (Ubuntu/Debian):
```bash
sudo apt update
sudo apt install openjdk-17-jdk
```

#### macOS:
```bash
brew install openjdk@17
```

### 2. Maven Kurulumu

#### Windows:
1. [Maven Binary zip](https://maven.apache.org/download.cgi) indir
2. Arşivi çıkart (örn: `C:\apache-maven-3.9.x`)
3. Sistem ortam değişkenlerine `MAVEN_HOME` ekle
4. `PATH` değişkenine `%MAVEN_HOME%\bin` ekle

#### Linux (Ubuntu/Debian):
```bash
sudo apt update
sudo apt install maven
```

#### macOS:
```bash
brew install maven
```

### 3. Proje Bağımlılıklarını Yükleme

Proje dizininde (`distributed-disk-register` klasörü içinde):

```bash
mvn clean install
```

veya sadece bağımlılıkları indirmek için:

```bash
mvn dependency:resolve
```

### 4. Protobuf Kod Üretimi ve Derleme

```bash
mvn clean compile
```

Bu komut:
- `src/main/proto/family.proto` dosyasından Java sınıfları oluşturur
- Tüm Java kaynak kodlarını derler
- Gerekli gRPC stub'larını üretir

## 🚀 Çalıştırma

### İlk Üye (Leader Node):
```bash
mvn exec:java -Dexec.mainClass=com.example.family.NodeMain
```

### İkinci, Üçüncü, vb. Üyeler:
Farklı terminal/command prompt pencerelerinde aynı komutu çalıştır:
```bash
mvn exec:java -Dexec.mainClass=com.example.family.NodeMain
```

### TCP Mesaj Gönderme:
```bash
nc 127.0.0.1 6666
# veya
telnet 127.0.0.1 6666
```

## 🔍 Sorun Giderme

### Maven bağımlılıkları indirilemiyor:
```bash
mvn clean install -U
```

### Protobuf derleme hatası:
```bash
mvn clean
mvn protobuf:compile
mvn protobuf:compile-custom
```

### Port zaten kullanımda:
- 5555-5560 arası portların boş olduğundan emin olun
- Lider node için 6666 portunun boş olduğunu kontrol edin

### Java versiyonu uyumsuzluğu:
```bash
java -version  # Java 17 veya üzeri olmalı
mvn -version   # JDK 17+ kullandığından emin ol
```

## 📚 Ek Kaynaklar

- [gRPC Java Documentation](https://grpc.io/docs/languages/java/)
- [Protocol Buffers Guide](https://protobuf.dev/)
- [Maven Getting Started](https://maven.apache.org/guides/getting-started/)

## 💡 Notlar

- Windows kullanıcıları için: PowerShell veya CMD yönetici olarak çalıştırılmalı
- Firewall ayarları 5555-5560 ve 6666 portlarına izin vermeli
- IntelliJ IDEA kullanıyorsanız: Projeyi `pom.xml` dosyasından açın
- Çoklu node test için en az 3 terminal penceresi açın
