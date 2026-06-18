import Link from "next/link";
import styles from "./page.module.css";

export default function Home() {
  return (
    <div className={styles.page}>
      <main className={styles.main}>
        <h1>AutoDispatch</h1>
        <p>Auto-rickshaw rides on campus — book in one tap.</p>
        <Link href="/book">Book a ride</Link>
      </main>
    </div>
  );
}
